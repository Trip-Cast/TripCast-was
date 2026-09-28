# AWS 배포 가이드 (EC2 + RDS + GitHub Actions)

TripCast를 AWS에 배포하는 방법을 정리한다. 구성은 다음과 같다.

```
GitHub (main push)
  └─ Test 워크플로우 통과
       └─ Deploy 워크플로우
            1. Docker 이미지 빌드 → ECR push
            2. EC2에 SSH 접속 → 새 이미지 pull → 컨테이너 재시작

EC2 (Docker, 애플리케이션) ──── RDS MySQL (TripCast DB)
```

- **EC2 + Docker**: 인스턴스를 직접 관리해야 하지만 비용이 가장 저렴하고(프리티어 가능) 지금까지 만든 Dockerfile을 그대로 재사용할 수 있어 학습 목적에 적합하다. ECS/Fargate는 서버 관리가 필요 없는 대신 VPC·태스크 정의·로드밸런서 설정이 늘어나 초기 러닝커브가 더 크다.
- **RDS MySQL**: 백업, 패치, 장애 조치를 AWS가 대신 처리한다. 프리티어(`db.t3.micro`, 12개월)로 시작할 수 있어 EC2에 직접 MySQL을 올리는 것보다 실무 구성에 가깝다.

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

퍼블릭 IP 확인:

```bash
aws ec2 describe-instances --filters "Name=tag:Name,Values=tripcast" \
  --query 'Reservations[0].Instances[0].PublicIpAddress' --output text
```

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
EOF
chmod 600 ~/tripcast/app.env
```

## 6. GitHub Secrets 등록

리포지토리 Settings → Secrets and variables → Actions에 등록한다.

| Secret | 값 |
| --- | --- |
| `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` | 배포 전용 IAM 사용자(ECR push 권한만 부여 권장) |
| `AWS_REGION` | `ap-northeast-2` |
| `ECR_REPOSITORY` | `tripcast` |
| `EC2_HOST` | EC2 퍼블릭 IP 또는 도메인 |
| `EC2_USERNAME` | `ec2-user` |
| `EC2_SSH_PRIVATE_KEY` | `tripcast-key.pem` 파일 내용 전체 |

이 저장소에는 [.github/workflows/deploy.yml](../.github/workflows/deploy.yml)이 이미 추가되어 있다. `main`에 push되어 기존 `Test` 워크플로우가 성공하면 자동으로 이어서 실행되어, 이미지를 빌드해 ECR에 올리고 EC2에서 컨테이너를 재시작한다.

## 7. 최초 배포 확인

Secrets 등록 후 `main`에 push하면 Actions 탭에서 `Test` → `Deploy` 순서로 실행되는 것을 볼 수 있다. 완료 후:

```bash
curl http://<EC2_퍼블릭IP>:8080/swagger-ui.html
```

Flyway는 컨테이너 기동 시 자동으로 스키마를 생성하므로 RDS에 별도로 DDL을 실행할 필요는 없다([docs/flyway-migration.md](flyway-migration.md) 참고).

## 8. 비용 관리 메모

- `db.t3.micro` + `t3.micro`는 프리티어 대상이지만 계정의 프리티어 사용 기간을 확인할 것.
- 사용하지 않을 때는 `aws ec2 stop-instances`, `aws rds stop-db-instance`로 멈춰서 과금을 줄일 수 있다(RDS는 최대 7일까지만 정지 유지, 이후 자동 재시작됨).
- 리소스를 완전히 정리하려면 EC2 인스턴스 종료 → RDS 인스턴스 삭제(최종 스냅샷 여부 선택) → ECR 리포지토리 삭제 → 보안 그룹/IAM 역할 삭제 순으로 진행한다.
