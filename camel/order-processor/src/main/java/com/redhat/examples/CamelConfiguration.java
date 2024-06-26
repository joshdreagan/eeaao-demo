/*
 * Copyright 2016 Red Hat, Inc.
 * <p>
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 *
 */
package com.redhat.examples;

import com.redhat.examples.json.ProcessedOrder;
import java.util.UUID;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.camel.AggregationStrategy;
import org.apache.camel.component.infinispan.InfinispanConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
public class CamelConfiguration extends RouteBuilder {

  private static final Logger log = LoggerFactory.getLogger(CamelConfiguration.class);

  @Bean
  @SuppressWarnings("unused")
  private AggregationStrategy descriptionEnrichmentStrategy() {
    return (Exchange original, Exchange resource) -> {
      if (resource.getIn().getBody() != null) {
        original.getIn().getBody(ProcessedOrder.class).setDescription(resource.getIn().getBody(String.class));
      }
      return original;
    };
  }

  @Override
  public void configure() throws Exception {
    from("kafka:raw?autoOffsetReset=earliest&groupId=camel-processor")
      .log(LoggingLevel.INFO, log, "Picked up raw order: [${body}]")
      .unmarshal().jaxb("com.redhat.examples.xml")
			.to("language:groovy:resource:classpath:/transformations/raw-order-to-processed-order.groovy")
      .process((exchange) -> {
        exchange.getIn().getBody(ProcessedOrder.class).setId(UUID.randomUUID().toString());
      })
      .enrich()
        .constant("direct:fetchDescription")
        .aggregationStrategy("descriptionEnrichmentStrategy")
      .end()
      .marshal().json(JsonLibrary.Jackson, false)
      .log(LoggingLevel.INFO, log, "Sending processed order: [${body}]")
      .to(ExchangePattern.InOnly, "kafka:processed")
    ;

    from("direct:fetchDescription")
      .setHeader("ItemID", simple("${body.item}"))
      .setHeader(InfinispanConstants.KEY, header("ItemID"))
      .to("infinispan:item-description-cache?operation=GET")
      .choice()
        .when(body().isNotNull())
          .log("Fetched item description from cache: [${body}]")
        .endChoice()
        .otherwise()
          .to("sql:select Description from ItemDescription where id=:#${headers.ItemID}?dataSource=#dataSource&outputType=SelectOne")
          .log("Fetched item description from DB: [${body}]")
          .setHeader(InfinispanConstants.VALUE, body())
          .to("infinispan:item-description-cache?operation=PUT")
        .endChoice()
    ;
  }
}
