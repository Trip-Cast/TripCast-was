# AWS 배포 가이드 (EC2 + RDS + GitHub Actions)

> **⚠️ 현재 운영 중단 상태 (2026-10-17 기준)**
>
> AWS 청구 금액이 늘어나 이 문서의 리소스(EC2, RDS, ECR, EBS)를 모두 삭제했다. 따라서 지금은 AWS에 배포된 서비스가 없고, `main`에 push해도 자동 배포는 실행되지 않는다([deploy.yml](../.github/workflows/deploy.yml)의 트리거를 `workflow_dispatch`로 바꿔 두었다).
>
> 아래 내용은 **다시 배포할 때 참고하는 기록**이다. 재배포하려면 다음을 순서대로 한다.
>
> 1. 이 문서의 1~5단계로 AWS 리소스를 다시 만든다.
> 2. GitHub Secrets를 새 값으로 등록한다(6단계).
> 3. `deploy.yml`의 `on:`을 주석으로 남겨 둔 원래 `workflow_run` 트리거로 되돌린다.
> 4. 최초 배포 확인(7단계)과 HTTPS 연결(8단계)을 진행한다.
>
> 비용을 관리하려면 재배포 전에 AWS Budgets에서 월 예산 알림(예: $5)을 먼저 걸어 둔다. 프리티어가 끝나면 EC2, RDS, 퍼블릭 IPv4 주소에 요금이 발생한다.

TripCast를 AWS에 배포하는 방법을 정리한다. 구성은 다음과 같다.

```
GitHub (main push)
  └─ Test 워크플로우 통과
       └─ Deploy 워크플로우
            1. Docker 이미지 빌드 → ECR push
            2. AWS SSM(Systems Manager)으로 EC2에 명령 전송 → 새 이미지 pull → 컨테이너 재시작

사용자 → https://<이름>.duckdns.org → EC2 Nginx(443/80, Let's Encrypt) → 127.0.0.1:8080 (Docker, 애플리케이션) ──── RDS MySQL (TripCast DB)
```

- **EC2 + Docker**: 인스턴스를 직접 관리해야 하지만 비용이 가장 저렴하고(프리티어 가능) 지금까지 만든 Dockerfile을 그대로 재사용할 수 있어 학습 목적에 적합하다. ECS/Fargate는 서버 관리가 필요 없는 대신 VPC·태스크 정의·로드밸런서 설정이 늘어나 초기 러닝커브가 더 크다.
- **RDS MySQL**: 백업, 패치, 장애 조치를 AWS가 대신 처리한다. 프리티어(`db.t3.micro`, 12개월)로 시작할 수 있어 EC2에 직접 MySQL을 올리는 것보다 실무 구성에 가깝다.
- **배포는 SSH가 아니라 SSM으로**: GitHub Actions 러너는 매번 IP가 바뀌기 때문에 "특정 IP에서만 22번 포트 허용"으로는 CI에서 SSH 접속이 안 된다. 그렇다고 22번을 전체 공개하면 보안그룹이 뚫려 있는 것이라 바람직하지 않다. AWS Systems Manager(SSM)의 `send-command`를 쓰면 22번 포트를 아예 열지 않고도 AWS API로 EC2에 원격 명령을 보낼 수 있다. 사람이 직접 접속할 때(5단계)는 SSH를 그대로 쓰고, CI 자동 배포만 SSM으로 처리한다.

이 문서의 AWS CLI 명령은 리전 `ap-northeast-2`(서울) 기준이다. 콘솔 스크린샷 위치는 UI 업데이트로 바뀔 수 있어 CLI 명령 위주로 적었고, 콘솔로 진행해도 무방하다.

---

## 0. 사전 준비

- AWS 계정 (기존 계정 사용)
- 로컬에 [AWS CLI](https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html) 설치 후 `aws configure`로 자격 증명 등록
- 리전은 이후 모든 명령에서 `--region ap-northeast-2`를 붙이거나 `aws configure set region ap-northeast-2`로 기본값 설정

## 1. 보안 그룹 생성

EC2용, RDS용 보안 그룹을 분리한다. RDS는 EC2에서만 접근 가능해야 한다.

```bash
VPC_ID=$(aws ec2 describe-vpcs --filters Name=isDefault,Values=true --query 'Vpcs[0].VpcId' --output text)

EC2_SG_ID=$(aws ec2 create-security-group \
  --group-name tripcast-ec2-sg --description "TripCast EC2" --vpc-id "$VPC_ID" \
  --query 'GroupId' --output text)

RDS_SG_ID=$(aws ec2 create-security-group \
  --group-name tripcast-rds-sg --description "TripCast RDS" --vpc-id "$VPC_ID" \
  --query 'GroupId' --output text)

# 내 IP에서만 SSH 허용 (매번 바뀌면 값 갱신 필요)
MY_IP=$(curl -s ifconfig.me)
aws ec2 authorize-security-group-ingress --group-id "$EC2_SG_ID" \
  --protocol tcp --port 22 --cidr "${MY_IP}/32"

# 앱 포트는 우선 전체 공개 (학습/검증용). 운영 수준으로 갈 때는 ALB 뒤로 옮기고 이 규칙은 제거한다.
aws ec2 authorize-security-group-ingress --group-id "$EC2_SG_ID" \
  --protocol tcp --port 8080 --cidr 0.0.0.0/0

# RDS는 EC2 보안그룹에서 오는 3306만 허용
aws ec2 authorize-security-group-ingress --group-id "$RDS_SG_ID" \
  --protocol tcp --port 3306 --source-group "$EC2_SG_ID"
```

## 2. RDS MySQL 생성

```bash
aws rds create-db-instance \
  --db-instance-identifier tripcast-db \
  --db-instance-class db.t3.micro \
  --engine mysql \
  --engine-version 8.0 \
  --master-username tripcast_admin \
  --master-user-password "<강력한 비밀번호로 교체>" \
  --allocated-storage 20 \
  --db-name TripCast \
  --vpc-security-group-ids "$RDS_SG_ID" \
  --backup-retention-period 7 \
  --no-publicly-accessible
```

> RDS MySQL은 마스터 사용자명으로 `root`를 허용하지 않는다. 이 저장소는 `DB_USERNAME` 환경변수로 계정명을 바꿀 수 있도록 이미 반영되어 있다([application.properties](../src/main/resources/application.properties)).

생성이 끝나면(`aws rds describe-db-instances --db-instance-identifier tripcast-db --query 'DBInstances[0].DBInstanceStatus'`가 `available`이 될 때까지 몇 분 소요) 엔드포인트를 확인한다.

```bash
aws rds describe-db-instances --db-instance-identifier tripcast-db \
  --query 'DBInstances[0].Endpoint.Address' --output text
```

## 3. ECR 리포지토리 생성

```bash
aws ecr create-repository --repository-name tripcast
```

## 4. EC2 인스턴스 생성

ECR을 읽을 수 있는 IAM 역할을 먼저 만든다.

```bash
aws iam create-role --role-name tripcast-ec2-role \
  --assume-role-policy-document '{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"Service":"ec2.amazonaws.com"},"Action":"sts:AssumeRole"}]}'

aws iam attach-role-policy --role-name tripcast-ec2-role \
  --policy-arn arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly

# GitHub Actions가 SSH 대신 SSM으로 배포 명령을 보낼 수 있게 하는 권한
aws iam attach-role-policy --role-name tripcast-ec2-role \
  --policy-arn arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore

aws iam create-instance-profile --instance-profile-name tripcast-ec2-profile
aws iam add-role-to-instance-profile --instance-profile-name tripcast-ec2-profile --role-name tripcast-ec2-role
```

SSH 키 페어와 인스턴스를 생성한다(이미 키 페어가 있으면 생략).

```bash
aws ec2 create-key-pair --key-name tripcast-key --query 'KeyMaterial' --output text > tripcast-key.pem
chmod 400 tripcast-key.pem

AMI_ID=$(aws ssm get-parameters --names /aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64 \
  --query 'Parameters[0].Value' --output text)

aws ec2 run-instances \
  --image-id "$AMI_ID" \
  --instance-type t3.micro \
  --key-name tripcast-key \
  --security-group-ids "$EC2_SG_ID" \
  --iam-instance-profile Name=tripcast-ec2-profile \
  --user-data '#!/bin/bash
dnf install -y docker
systemctl enable --now docker
usermod -aG docker ec2-user' \
  --tag-specifications 'ResourceType=instance,Tags=[{Key=Name,Value=tripcast}]'
```

퍼블릭 IP와 인스턴스 ID 확인:

```bash
aws ec2 describe-instances --filters "Name=tag:Name,Values=tripcast" \
  --query 'Reservations[0].Instances[0].[InstanceId,PublicIpAddress]' --output text
```

인스턴스 ID는 6단계에서 `EC2_INSTANCE_ID` 시크릿으로 쓰인다. SSM에 정상 등록됐는지도 확인한다(부팅 후 1~2분 소요, `AmazonSSMManagedInstanceCore` 정책이 붙어 있어야 한다).

```bash
aws ssm describe-instance-information --filters "Key=InstanceIds,Values=<INSTANCE_ID>"
```

`"PingStatus": "Online"`이 보이면 SSM으로 명령을 받을 준비가 된 것이다.

## 5. EC2에 런타임 환경변수 파일 준비

애플리케이션 비밀값(DB 비밀번호, API 키 등)은 GitHub Actions를 거치지 않고 EC2에만 저장한다. GitHub Secrets에는 AWS 자격 증명과 SSH 접속 정보만 둔다.

```bash
ssh -i tripcast-key.pem ec2-user@<EC2_퍼블릭IP>

mkdir -p ~/tripcast
cat > ~/tripcast/app.env <<'EOF'
DB_HOST=<2단계에서 확인한 RDS 엔드포인트>
DB_PORT=3306
DB_USERNAME=tripcast_admin
DB_PASSWORD=<RDS 마스터 비밀번호>
WEATHER_API_KEY=<기상청 API 키>
TOUR_CLIMATE_API_KEY=<공공데이터포털 API 키>
JWT_SECRET=<32자 이상 랜덤 문자열>
ADMIN_USERNAME=<관리자 계정>
ADMIN_PASSWORD=<관리자 비밀번호>
CORS_ALLOWED_ORIGINS=https://<이름>.duckdns.org,http://localhost:5173
EOF
chmod 600 ~/tripcast/app.env
```

`CORS_ALLOWED_ORIGINS`는 기본값(로컬 개발용 localhost)을 통째로 덮어쓰므로, 로컬 개발 origin을 유지하려면 위처럼 콤마로 함께 적는다. 프론트가 same-origin(`/api`)으로 호출하더라도 필요하다. 프록시가 `X-Forwarded-Proto`를 `http`로 넘기면 Spring이 요청을 `http://` 기준으로 보고, `Origin: https://...` 헤더가 붙는 POST/PUT/DELETE를 cross-origin으로 판정해 `403 Invalid CORS request`를 반환하기 때문이다. 프록시가 `https`를 올바르게 전달하면 same-origin으로 인식되지만, 허용 목록에 넣어 두면 어느 경우에나 안전하다.

> `docker run --env-file`은 컨테이너를 만들 때만 파일을 읽는다. `app.env`를 고친 뒤 `docker restart tripcast`로는 반영되지 않으므로, 컨테이너를 다시 만들거나(`docker stop tripcast && docker rm tripcast` 후 `docker run ...`) 배포 워크플로를 다시 실행한다.

## 6. GitHub Secrets 등록

배포 전용 IAM 사용자(또는 로컬에서 쓰던 사용자)에 ECR push 권한과 함께, SSM으로 명령을 보낼 수 있는 권한도 필요하다.

```bash
aws iam attach-user-policy --user-name <배포용 IAM 사용자> \
  --policy-arn arn:aws:iam::aws:policy/AmazonSSMFullAccess
```

리포지토리 Settings → Secrets and variables → Actions에 등록한다.

| Secret | 값 |
| --- | --- |
| `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` | 배포 전용 IAM 사용자 자격 증명 |
| `AWS_REGION` | `ap-northeast-2` |
| `ECR_REPOSITORY` | `tripcast` |
| `EC2_INSTANCE_ID` | 4단계에서 확인한 EC2 인스턴스 ID (예: `i-0123456789abcdef0`) |

`EC2_HOST`, `EC2_USERNAME`, `EC2_SSH_PRIVATE_KEY`는 CI 자동 배포에는 더 이상 쓰이지 않는다(5단계처럼 사람이 직접 SSH로 들어갈 때만 필요). 등록해뒀다면 그대로 둬도 되고, 안 쓸 거면 지워도 된다.

이 저장소에는 [.github/workflows/deploy.yml](../.github/workflows/deploy.yml)이 이미 추가되어 있다. `main`에 push되어 기존 `Test` 워크플로우가 성공하면 자동으로 이어서 실행되어, 이미지를 빌드해 ECR에 올리고 `aws ssm send-command`로 EC2에 배포 명령을 보낸다.

## 7. 최초 배포 확인

Secrets 등록 후 `main`에 push하면 Actions 탭에서 `Test` → `Deploy` 순서로 실행되는 것을 볼 수 있다. 완료 후:

```bash
curl http://<EC2_퍼블릭IP>:8080/swagger-ui.html
```

Flyway는 컨테이너 기동 시 자동으로 스키마를 생성하므로 RDS에 별도로 DDL을 실행할 필요는 없다([docs/flyway-migration.md](flyway-migration.md) 참고).

## 8. HTTPS 도메인 연결 (DuckDNS + Nginx + Let's Encrypt)

퍼블릭 IP를 그대로 쓰면 HTTP로만 접근 가능하고, IP가 바뀌면 접속 주소도 바뀐다. 도메인이 필요한데 구매 비용 없이 해결하려면 무료 동적 DNS(DuckDNS) + 무료 인증서(Let's Encrypt) 조합을 쓴다.

```
사용자 → https://<이름>.duckdns.org → EC2 Nginx(443/80) → 127.0.0.1:8080 (Spring Boot 컨테이너)
```

### 8-1. Elastic IP 고정

EC2를 멈췄다 켜면 퍼블릭 IP가 바뀔 수 있어서, 도메인이 가리킬 IP를 고정해야 한다.

```bash
EIP_ALLOC=$(aws ec2 allocate-address --domain vpc --query 'AllocationId' --output text)
aws ec2 associate-address --instance-id <INSTANCE_ID> --allocation-id "$EIP_ALLOC"
aws ec2 describe-instances --instance-ids <INSTANCE_ID> --query 'Reservations[0].Instances[0].PublicIpAddress' --output text
```

> Elastic IP는 인스턴스에 붙어서 실행 중일 때는 무료지만, 인스턴스를 중지한 채로 오래 두면 IP만 따로 과금된다.

### 8-2. DuckDNS 서브도메인 발급

1. https://www.duckdns.org 접속 → 로그인 → reCAPTCHA 통과
2. 상단에 보이는 토큰 확인(자동 갱신 스크립트를 쓸 경우 필요, Elastic IP로 고정했다면 필수는 아니다)
3. subdomain 입력 후 add domain → 방금 받은 Elastic IP를 입력하고 update ip

`nslookup <이름>.duckdns.org`로 Elastic IP와 같은 주소가 나오는지 확인한다.

### 8-3. 보안그룹에 80/443 열기

```bash
aws ec2 authorize-security-group-ingress --group-id "$EC2_SG_ID" --protocol tcp --port 80 --cidr 0.0.0.0/0
aws ec2 authorize-security-group-ingress --group-id "$EC2_SG_ID" --protocol tcp --port 443 --cidr 0.0.0.0/0
```

80번도 반드시 열어야 한다 — Let's Encrypt가 인증서를 발급할 때 HTTP-01 챌린지로 80번에 접근해서 도메인 소유를 확인하기 때문에, 80번이 막혀 있으면 `Timeout during connect` 에러로 발급이 실패한다.

### 8-4. Nginx + certbot 설치, 리버스 프록시 설정

EC2 안에서(SSH 접속 후):

```bash
sudo dnf install -y nginx
sudo systemctl enable --now nginx

sudo tee /etc/nginx/conf.d/tripcast.conf > /dev/null <<'EOF'
server {
    listen 80;
    server_name <이름>.duckdns.org;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
EOF

sudo nginx -t
sudo systemctl reload nginx
```

nginx가 `X-Forwarded-Proto` 헤더로 원래 요청 스킴(HTTPS)을 전달해도, Spring Boot는 기본적으로 이 헤더를 신뢰하지 않는다. `server.forward-headers-strategy=framework`(`application.properties`)를 설정해야 Swagger `servers.url` 등에서 `http://`가 아닌 실제 `https://` 스킴이 반영된다.

```bash

# Amazon Linux 2023은 pip으로 certbot 설치가 공식 권장 방법
sudo dnf install -y python3-pip augeas-libs
sudo python3 -m venv /opt/certbot
sudo /opt/certbot/bin/pip install --upgrade pip
sudo /opt/certbot/bin/pip install certbot certbot-nginx
sudo ln -s /opt/certbot/bin/certbot /usr/bin/certbot
```

### 8-5. 인증서 발급 및 자동 갱신

```bash
sudo certbot --nginx -d <이름>.duckdns.org --non-interactive --agree-tos -m <본인 이메일> --redirect
```

`--redirect`를 쓰면 certbot이 Nginx 설정에 443 서버 블록을 추가하고 HTTP→HTTPS 리다이렉트까지 자동으로 구성해준다. 인증서는 90일마다 갱신이 필요하므로 크론으로 자동화한다.

```bash
echo "0 0,12 * * * root /opt/certbot/bin/certbot renew -q" | sudo tee -a /etc/crontab
```

### 8-6. 8080 직접 노출 차단

Nginx가 443/80에서 8080으로 내부 전달을 해주므로, 8080을 외부에 그대로 열어둘 필요가 없다.

```bash
aws ec2 revoke-security-group-ingress --group-id "$EC2_SG_ID" --protocol tcp --port 8080 --cidr 0.0.0.0/0
```

이후로는 `https://<이름>.duckdns.org/swagger-ui.html`로만 접근 가능하고, `http://<IP>:8080`은 막힌다.

## 9. 비용 관리 메모

- `db.t3.micro` + `t3.micro`는 프리티어 대상이지만 계정의 프리티어 사용 기간을 확인할 것.
- 사용하지 않을 때는 `aws ec2 stop-instances`, `aws rds stop-db-instance`로 멈춰서 과금을 줄일 수 있다(RDS는 최대 7일까지만 정지 유지, 이후 자동 재시작됨).
- 리소스를 완전히 정리하려면 EC2 인스턴스 종료 → RDS 인스턴스 삭제(최종 스냅샷 여부 선택) → ECR 리포지토리 삭제 → 보안 그룹/IAM 역할 삭제 순으로 진행한다.
- 인스턴스를 **중지**만 해서는 요금이 완전히 멈추지 않는다. EBS 볼륨, Elastic IP(연결되지 않은 경우), 퍼블릭 IPv4 주소에는 계속 요금이 붙는다. 쓰지 않을 때는 종료(terminate)하는 편이 낫다.
- 정리한 뒤에는 다음을 확인한다.
  - `aws ec2 describe-instances`, `describe-volumes`, `describe-addresses`로 남은 인스턴스, 볼륨, Elastic IP가 없는지 본다(`--region ap-northeast-2`).
  - Billing → Cost Explorer에서 서비스별 요금이 0으로 떨어졌는지 하루 이틀 뒤 확인한다.
  - 배포용 IAM 사용자의 액세스 키를 삭제하고 GitHub Secrets도 함께 지운다.
- AWS Budgets에 월 예산 알림을 걸어 두면 요금이 늘어나는 것을 일찍 알 수 있다.
