package edu.berkeley.cs.amplab.carat.dynamodb

import edu.berkeley.cs.amplab.carat._
import com.amazonaws.services.dynamodb.model.AttributeValue
import collection.JavaConversions._

/**
 * Program to copy analyzed data of a certain uuid under osVersion 5.0 for Simulator data access.
 */
object DeleteDataForUuid {
  def main(args: Array[String]) {
    if (args != null && args.length >= 1) {
      if (args.length > 1 && args(1) == "force")
        force = true
      deleteDataForUuid(args(0))
    }
  }
  
  var force = false

  def deleteDataForUuid(uuId: String) {
    if (uuId != null) {
      println("Getting stuff from "+samplesTable)
      var (key, res) = DynamoDbDecoder.getItems(samplesTable, uuId)
      for (k <- res){
        val key = k.get(sampleKey).getS()
        val time = k.get(sampleTime).getN().toDouble
        printf("key %s time %s\n", key, time)
        if (force)
          DynamoDbDecoder.deleteItem(samplesTable, key, time)
      }
      
       println("Getting stuff from "+registrationTable)
      var (key2, res2) = DynamoDbDecoder.getItems(registrationTable, uuId)
      for (k <- res2){
        val key = k.get(regsUuid).getS()
        val time = k.get(regsTimestamp).getN().toDouble
        printf("key %s time %s\n", key, time)
        if (force)
          DynamoDbDecoder.deleteItem(registrationTable, key, time)
      }
    }
  }
}
