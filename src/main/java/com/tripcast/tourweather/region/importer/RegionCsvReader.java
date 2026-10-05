package com.tripcast.tourweather.region.importer;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class RegionCsvReader {

    public List<RegionCsvRow> read() {
        ClassPathResource resource =
                new ClassPathResource("data/regions.csv");

        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .get();

        try (
                Reader reader = new InputStreamReader(
                        resource.getInputStream(),
                        StandardCharsets.UTF_8
                );
                CSVParser parser = format.parse(reader)
        ) {
            return parser.stream()
                    .map(this::toRow)
                    .toList();

        } catch (IOException exception) {
            throw new IllegalStateException(
                    "지역 CSV 파일을 읽을 수 없습니다.",
                    exception
            );
        }
    }

    private RegionCsvRow toRow(CSVRecord record) {
        try {
            return new RegionCsvRow(
                    record.get("지역 아이디"),
                    record.get("시도명"),
                    record.get("시군구명")
            );

        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(
                    "지역 CSV 데이터 " + record.getRecordNumber()
                            + "번째 행을 변환할 수 없습니다.",
                    exception
            );
        }
    }
}
