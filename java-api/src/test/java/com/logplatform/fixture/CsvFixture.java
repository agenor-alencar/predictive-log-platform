package com.logplatform.fixture;

/**
 * Reusable CSV content for integration tests.
 */
public final class CsvFixture {

    private CsvFixture() {}

    public static final String HEADER =
            "timestamp,method,path,status_code,response_time_ms,user_agent,ip_address,bytes_sent";

    public static String validCsv(int rows) {
        StringBuilder sb = new StringBuilder(HEADER).append('\n');
        for (int i = 0; i < rows; i++) {
            sb.append("2025-01-15T10:").append(String.format("%02d", i % 60)).append(":00,")
              .append(i % 2 == 0 ? "GET" : "POST").append(',')
              .append("/api/resource/").append(i).append(',')
              .append(i % 5 == 0 ? 500 : 200).append(',')
              .append(100 + i * 3).append(".0,")
              .append("Mozilla/5.0,")
              .append("192.168.1.").append(i % 255 + 1).append(',')
              .append(1024)
              .append('\n');
        }
        return sb.toString();
    }

    public static final String SINGLE_ROW_CSV =
            HEADER + "\n" +
            "2025-06-01T08:30:00,GET,/api/health,200,45.0,curl/8.4.0,10.0.0.1,512\n";

    public static final String NON_CSV_CONTENT = "this is not a csv file at all";

    public static final String EMPTY_CSV = "";

    public static final String CSV_WITH_INVALID_ROWS =
            HEADER + "\n" +
            "2025-06-01T08:30:00,GET,/api/health,200,45.0,curl/8.4.0,10.0.0.1,512\n" +
            "bad_row_missing_fields\n" +
            "2025-06-01T08:31:00,POST,/api/data,201,120.0,Mozilla,10.0.0.2,2048\n";
}
