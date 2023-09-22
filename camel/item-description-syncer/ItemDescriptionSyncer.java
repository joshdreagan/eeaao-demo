// camel-k: language=java
// camel-k: dependency=camel-quarkus-groovy

import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;

public class ItemDescriptionSyncer extends RouteBuilder {

  @Override
  public void configure() throws Exception {

    from("kafka:{{kafka.topic}}")
			.log("Picked up CDC message: [${body}]")
			.filter(body().isNull())
				.stop()
			.end()
			.unmarshal().json(JsonLibrary.Jackson, Map.class)
			.setHeader("DebeziumOperation").groovy("request.body.payload?.op")
			.routingSlip().simple("direct:${header.DebeziumOperation}")
		;

		from("direct:c")
	    .transform().groovy("request.body.payload?.after")
			.setHeader("CamelInfinispanKey").groovy("request.body?.Id")
			.setHeader("CamelInfinispanValue").groovy("request.body?.Description")
			.log("Inserting record into cache: [${headers.CamelInfinispanKey}=${headers.CamelInfinispanValue}]")
      .to("infinispan:item-description-cache?operation=PUT")
    ;

    from("direct:u")
      .transform().groovy("request.body.payload?.after")
			.setHeader("CamelInfinispanKey").groovy("request.body?.Id")
			.setHeader("CamelInfinispanValue").groovy("request.body?.Description")
			.log("Updating record in cache: [${headers.CamelInfinispanKey}=${headers.CamelInfinispanValue}]")
			.to("infinispan:item-description-cache?operation=REPLACE")
    ;

    from("direct:d")
      .transform().groovy("request.body.payload?.before")
			.setHeader("CamelInfinispanKey").groovy("request.body?.Id")
			.log("Removing record from cache: [${headers.CamelInfinispanKey}]")
			.to("infinispan:item-description-cache?operation=REMOVE")
    ;
  }
}
