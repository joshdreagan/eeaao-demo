// camel-k: language=java
// camel-k: dependency=camel-quarkus-groovy
// camel-k: dependency=camel-zipfile

import java.util.ArrayList;
import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.camel.AggregationStrategy;
import org.apache.camel.processor.aggregate.zipfile.ZipAggregationStrategy;

public class OrderArchiver extends RouteBuilder {

  @Override
  public void configure() throws Exception {

		AggregationStrategy orderAggregationStrategy = new ZipAggregationStrategy(false, true);

    from("kafka:{{kafka.topic}}")
			.log("Picked up message: [${body}]")
			.setHeader("CamelFileName", simple("order-${header.CamelMessageTimestamp}.xml"))
			.aggregate()
			  .constant(true)
				.aggregationStrategy(orderAggregationStrategy)
				.completionTimeout(30000L)
				.completionSize(10)
					.setHeader("CurrentTimeMillis", method(System.class, "currentTimeMillis"))
					.setHeader("CamelAwsS3Key", simple("order-archive-${header.CurrentTimeMillis}.zip"))
					.setHeader("CamelAwsS3ContentType", constant("application/zip"))
					.log("Completing aggregate order: [${header.CamelAwsS3Key}]")
					.to("aws2-s3:{{s3.bucketName}}")
			.end()
		;
	}
}
