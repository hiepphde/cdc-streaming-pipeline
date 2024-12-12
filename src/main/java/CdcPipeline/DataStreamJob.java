/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package CdcPipeline;

import Deserializer.JSONValueDeserializationSchema;
import Dto.Transaction;
import Env.Env;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.connector.jdbc.JdbcConnectionOptions;
import org.apache.flink.connector.jdbc.JdbcExecutionOptions;
import org.apache.flink.connector.jdbc.JdbcSink;
import org.apache.flink.connector.jdbc.JdbcStatementBuilder;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

public class DataStreamJob {

	public static void main(String[] args) throws Exception {
		// Sets up the execution environment, which is the main entry point
		// to building Flink applications.
		final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

//		define variable
		String topic = Env.topic;
		String bootstrap_server = Env.bootstrap_server;
		String url = Env.jdbcUrl;
		String username = Env.username;
		String password = Env.password;

//		write data to stdout
//		Properties props = new Properties();
//		props.setProperty("bootstrap.servers", bootstrap_server);
//		props.setProperty("group.id","flink-group");
//		FlinkKafkaConsumer<Transaction> consumer = new FlinkKafkaConsumer<>(topic,new JSONValueDeserializationSchema(),props);
//		DataStream<Transaction> transactions = env.addSource(consumer);
//		transactions.print();

//		write data to postgres
		KafkaSource<Transaction> source = KafkaSource.<Transaction>builder()
				.setBootstrapServers(bootstrap_server)
				.setTopics(topic)
				.setGroupId("flink-group")
				.setStartingOffsets(OffsetsInitializer.earliest())
				.setValueOnlyDeserializer(new JSONValueDeserializationSchema())
				.build();

		DataStream<Transaction> transactionStream = env.fromSource(source, WatermarkStrategy.noWatermarks(),"Kafka source");
		transactionStream.print();
		DataStream<Transaction> insertStream = transactionStream.filter(transaction -> transaction.getOp().equals("c"));
		DataStream<Transaction> updateStream = transactionStream.filter(transaction -> transaction.getOp().equals("u"));
		DataStream<Transaction> deleteStream = transactionStream.filter(transaction -> transaction.getOp().equals("d"));

		JdbcExecutionOptions execOptions = new JdbcExecutionOptions.Builder()
				.withBatchSize(1000)
				.withBatchIntervalMs(200)
				.withMaxRetries(5)
				.build();
		JdbcConnectionOptions connOptions = new JdbcConnectionOptions.JdbcConnectionOptionsBuilder()
				.withUrl(url)
				.withUsername(username)
				.withPassword(password)
				.withDriverName("org.postgresql.Driver")
				.build();

//		insertStream
		final String insertSql = "INSERT INTO target_transactions (transaction_id, user_id, timestamp, amount, currency, city, country, merchant_name, payment_method, ip_address, voucher_code, affiliateid) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";
		insertStream.addSink(
				JdbcSink.sink(
						insertSql,
						(JdbcStatementBuilder<Transaction>) (statement, transaction) -> {
							statement.setString(1, transaction.getAfter().getTransaction_id());
							statement.setString(2, transaction.getAfter().getUser_id());
							statement.setTimestamp(3, transaction.getAfter().getTimestamp());
							statement.setDouble(4, transaction.getAfter().getAmount());
							statement.setString(5, transaction.getAfter().getCurrency());
							statement.setString(6, transaction.getAfter().getCity());
							statement.setString(7, transaction.getAfter().getCountry());
							statement.setString(8, transaction.getAfter().getMerchant_name());
							statement.setString(9, transaction.getAfter().getPayment_method());
							statement.setString(10, transaction.getAfter().getIp_address());
							statement.setString(11, transaction.getAfter().getVoucher_code());
							statement.setString(12, transaction.getAfter().getAffiliateid());
						},
						execOptions,
						connOptions
				)
		).name("Insert stream");
		//updateStream
		final String updateSql = "UPDATE target_transactions SET user_id =?, timestamp =?, amount =?, currency =?, city =?, country =?, merchant_name =?, payment_method =?, ip_address =?, voucher_code =?, affiliateid =? WHERE transaction_id =?";
		updateStream.addSink(
				JdbcSink.sink(
						updateSql,
						(JdbcStatementBuilder<Transaction>) (statement, transaction) -> {
							statement.setString(1, transaction.getAfter().getUser_id());
							statement.setTimestamp(2, transaction.getAfter().getTimestamp());
							statement.setDouble(3, transaction.getAfter().getAmount());
							statement.setString(4, transaction.getAfter().getCurrency());
							statement.setString(5, transaction.getAfter().getCity());
							statement.setString(6, transaction.getAfter().getCountry());
							statement.setString(7, transaction.getAfter().getMerchant_name());
							statement.setString(8, transaction.getAfter().getPayment_method());
							statement.setString(9, transaction.getAfter().getIp_address());
							statement.setString(10, transaction.getAfter().getVoucher_code());
							statement.setString(11, transaction.getAfter().getAffiliateid());
							statement.setString(12, transaction.getAfter().getTransaction_id());
						},
						execOptions,
						connOptions
				)
		).name("Update stream");
		//deleteStream
		final String deleteSql = "DELETE FROM target_transactions WHERE transaction_id =?";
		deleteStream.addSink(
				JdbcSink.sink(
						deleteSql,
						(JdbcStatementBuilder<Transaction>) (statement, transaction) -> {
							statement.setString(1, transaction.getBefore().getTransaction_id());
						},
						execOptions,
						connOptions
				)
		).name("Delete stream");
		// Execute program, beginning computation.
		env.execute("Cdc-transaction-realtime-pipeline");
	}
}
