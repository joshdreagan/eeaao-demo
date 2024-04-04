import com.redhat.examples.json.ProcessedOrder
import com.redhat.examples.xml.RawOrder

return new ProcessedOrder(
  id: null,
	customer: body.customerId,
	item: body.itemId,
	description: null,
	quantity: body.quantity
)
