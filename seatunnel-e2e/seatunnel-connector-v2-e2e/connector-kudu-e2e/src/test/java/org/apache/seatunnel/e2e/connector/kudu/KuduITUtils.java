package org.apache.seatunnel.e2e.connector.kudu;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class KuduITUtils {
    public static String getLocalTimeStr(String str) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

        LocalDateTime localDateTime = LocalDateTime.parse(str, formatter);

        ZonedDateTime zonedDateTime = localDateTime.atZone(java.time.ZoneId.systemDefault());

        DateTimeFormatter outFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.S");

        return zonedDateTime.format(outFormatter);
    }

    public static void main(String[] args) {
        String localTimeStr = KuduITUtils.getLocalTimeStr("2020-02-02T02:02:02");
        System.out.println(localTimeStr);
    }
}
