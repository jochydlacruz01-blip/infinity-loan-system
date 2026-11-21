package com.infinityloan.finance
import org.junit.Assert.*
import org.junit.Test
class CustomerDataIntegrityAuditTest {
 @Test fun flagsMissingFieldsAndDuplicateNormalizedDocument(){
  val findings=CustomerDataIntegrityAudit.audit(listOf(
   CustomerAuditRecord("C1","Ana Perez","001-1234567-8","8095551234"),
   CustomerAuditRecord("C2","Ana P.","001 1234567 8",""),
   CustomerAuditRecord("C3","","","")
  ))
  assertEquals(2,findings.count{it.type==CustomerDataIssueType.DUPLICATE_DOCUMENT})
  assertTrue(findings.any{it.type==CustomerDataIssueType.EMPTY_NAME&&it.customerId=="C3"})
  assertTrue(findings.any{it.type==CustomerDataIssueType.EMPTY_DOCUMENT&&it.customerId=="C3"})
  assertTrue(findings.any{it.type==CustomerDataIssueType.EMPTY_PHONE&&it.customerId=="C2"})
 }
 @Test fun ignoresFormattingDifferencesAndHealthyRecords(){
  assertTrue(CustomerDataIntegrityAudit.audit(listOf(CustomerAuditRecord("C1","Ana Perez","001-1234567-8","8095551234","Santo Domingo"))).isEmpty())
 }
 @Test fun deterministicallyReportsRepeatedRecords(){
  val input=listOf(CustomerAuditRecord("B","B","ABC-123"),CustomerAuditRecord("A","A","abc123"))
  val first=CustomerDataIntegrityAudit.audit(input)
  val second=CustomerDataIntegrityAudit.audit(input.reversed())
  assertEquals(first,second)
 }
}