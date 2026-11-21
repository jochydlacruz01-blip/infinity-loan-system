package com.infinityloan.finance

/** Safe, read-only customer master-data checks. Does not mutate stored records. */
data class CustomerAuditRecord(val id:String,val fullName:String,val document:String,val phone:String="",val address:String="")
enum class CustomerDataIssueType { EMPTY_ID, EMPTY_NAME, EMPTY_DOCUMENT, DUPLICATE_DOCUMENT, EMPTY_PHONE }
data class CustomerDataIssue(val type:CustomerDataIssueType,val customerId:String,val details:String)
object CustomerDataIntegrityAudit {
 fun audit(customers:List<CustomerAuditRecord>):List<CustomerDataIssue>{
  val findings=mutableListOf<CustomerDataIssue>()
  customers.forEach { c ->
   if(c.id.isBlank()) findings+=CustomerDataIssue(CustomerDataIssueType.EMPTY_ID,c.id,"Identificador del cliente vacío.")
   if(c.fullName.isBlank()) findings+=CustomerDataIssue(CustomerDataIssueType.EMPTY_NAME,c.id,"Nombre del cliente vacío.")
   if(c.document.isBlank()) findings+=CustomerDataIssue(CustomerDataIssueType.EMPTY_DOCUMENT,c.id,"Documento de identidad no registrado.")
   if(c.phone.isBlank()) findings+=CustomerDataIssue(CustomerDataIssueType.EMPTY_PHONE,c.id,"Teléfono no registrado.")
  }
  val groups=customers.filter{it.document.isNotBlank()}.groupBy{normalizeDocument(it.document)}
  groups.filter{it.key.isNotBlank()&&it.value.size>1}.values.forEach { group ->
   group.sortedBy{it.id}.forEach { c -> findings+=CustomerDataIssue(CustomerDataIssueType.DUPLICATE_DOCUMENT,c.id,"Documento repetido entre clientes: "+group.map{it.id}.sorted().joinToString(", ")) }
  }
  return findings.distinct().sortedWith(compareBy<CustomerDataIssue>{it.type.name}.thenBy{it.customerId})
 }
 private fun normalizeDocument(value:String)=value.trim().uppercase().filter{it.isLetterOrDigit()}
}