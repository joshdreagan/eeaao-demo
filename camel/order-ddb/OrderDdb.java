// camel-k: language=java dependency=camel-groovy
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;

public class OrderDdb extends RouteBuilder {

  @Override
  public void configure() throws Exception {

    from("kafka:{{kafka.topic}}")
      .log("Picked up message: [${body}]")
      .filter(body().isNull())
        .stop()
      .end()
      .unmarshal().json(JsonLibrary.Jackson, Map.class)
			.to("direct:insertDdb")
    ;

    from("direct:insertDdb")
    .setHeader("CamelAwsDdbItem").groovy(
        "import software.amazon.awssdk.services.dynamodb.model.AttributeValue;\n" +
        "var map = [:];\n" +
				"map['OrderId'] = AttributeValue.builder().s(request.body?.id).build();\n" +
        "map['CustomerId'] = AttributeValue.builder().s(request.body?.customer).build();\n" +
        "map['ItemId'] = AttributeValue.builder().s(request.body?.item).build();\n" +
        "map['Description'] = AttributeValue.builder().s(request.body?.description).build();\n" +
        "map['Quantity'] = AttributeValue.builder().n(request.body?.quantity as String).build();\n" +
        "return map;"
      )
      .to("aws2-ddb:{{dynamodb.tableName}}?operation=PutItem")
    ;
	}
}
