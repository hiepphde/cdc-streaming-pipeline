package Dto;

import lombok.Data;

import java.sql.Timestamp;

@Data
public class Transaction {
    private Before before;
    private After after;
    private Source source;
    private String op;
    private long ts_ms;
    private String transaction;

    @Data
    public static class Before {
        private String transaction_id;
        private String user_id;
        private Timestamp timestamp;
        private double amount;
        private String currency;
        private String city;
        private String country;
        private String merchant_name;
        private String payment_method;
        private String ip_address;
        private String voucher_code;
        private String affiliateid;
    }
    @Data
    public static class After {
        private String transaction_id;
        private String user_id;
        private Timestamp timestamp;
        private double amount;
        private String currency;
        private String city;
        private String country;
        private String merchant_name;
        private String payment_method;
        private String ip_address;
        private String voucher_code;
        private String affiliateid;
    }
    @Data
    public static class Source {
        private String version;
        private String connector;
        private String name;
        private Timestamp ts_ms;
        private String snapshot;
        private String db;
        private String sequence;
        private String schema;
        private String table;
        private long txId;
        private long lsn;
        private Object xmin;
    }
}

