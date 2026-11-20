package com.infinityloan.finance

import org.junit.Assert.*
import org.junit.Test

class CollectionWorkloadBalancerTest {
 @Test fun suggestsLowestLoadAndDeterministicTie(){
   assertEquals("c2",CollectionWorkloadBalancer.suggestCollector(listOf("c1","c2","c3"),mapOf("c1" to 4,"c2" to 1,"c3" to 1)))
   assertEquals("a",CollectionWorkloadBalancer.suggestCollector(listOf("b","a"),emptyMap()))
 }
 @Test fun rejectsEmptyOrNegativeLoad(){
   try { CollectionWorkloadBalancer.suggestCollector(emptyList(),emptyMap()); fail() } catch (_:IllegalArgumentException){}
   try { CollectionWorkloadBalancer.suggestCollector(listOf("a"),mapOf("a" to -1)); fail() } catch (_:IllegalArgumentException){}
 }
 @Test fun workloadReportsMissingAsZero(){
   assertEquals(mapOf("a" to 0,"b" to 2),CollectionWorkloadBalancer.workload(listOf("b","a"),mapOf("b" to 2)))
 }
}
