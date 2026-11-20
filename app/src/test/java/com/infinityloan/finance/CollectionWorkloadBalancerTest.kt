package com.infinityloan.finance

import org.junit.Assert.*
import org.junit.Test

class CollectionWorkloadBalancerTest {
 @Test fun suggestsLowestLoadAndDeterministicTie(){
   assertEquals("c2",CollectionWorkloadBalancer.suggestCollector(listOf("c1","c2","c3"),mapOf("c1" to 4,"c2" to 1,"c3" to 1)))
   assertEquals("a",CollectionWorkloadBalancer.suggestCollector(listOf("b","a"),emptyMap()))
 }
 @Test fun rejectsEmptyOrNegativeLoad(){
   assertThrows(IllegalArgumentException::class.java) { CollectionWorkloadBalancer.suggestCollector(emptyList(),emptyMap()) }
   assertThrows(IllegalArgumentException::class.java) { CollectionWorkloadBalancer.suggestCollector(listOf("a"),mapOf("a" to -1)) }
   assertThrows(IllegalArgumentException::class.java) { CollectionWorkloadBalancer.workload(listOf("a"),mapOf("a" to -1)) }
 }
 @Test fun workloadReportsMissingAsZeroAndDeduplicates(){
   assertEquals(mapOf("a" to 0,"b" to 2),CollectionWorkloadBalancer.workload(listOf("b","a","a"," "),mapOf("b" to 2)))
 }
 @Test fun ignoresBlankAndUnknownCollectorIdsAndDoesNotMutateInput(){
   val ids = listOf("b", " ", "a", "b")
   val counts = mutableMapOf("a" to 3, "b" to 1, "unlisted" to 99)
   assertEquals("b", CollectionWorkloadBalancer.suggestCollector(ids, counts))
   assertEquals(mapOf("a" to 3, "b" to 1), CollectionWorkloadBalancer.workload(ids, counts))
   assertEquals(listOf("b", " ", "a", "b"), ids)
   assertEquals(99, counts["unlisted"])
 }
 @Test fun workloadAllowsEmptyEligibleList(){
   assertEquals(emptyMap<String, Int>(), CollectionWorkloadBalancer.workload(listOf(" ", ""), emptyMap()))
 }
}
