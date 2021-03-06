package edu.berkeley.cs.amplab.carat.dynamodb

import com.amazonaws.services.dynamodb.AmazonDynamoDBClient
import com.amazonaws.services.dynamodb.model.CreateTableRequest
import com.amazonaws.services.dynamodb.model.DescribeTableRequest
import com.amazonaws.services.dynamodb.model.KeySchemaElement
import com.amazonaws.services.dynamodb.model.KeySchema
import com.amazonaws.services.dynamodb.model.ProvisionedThroughput
import com.amazonaws.services.dynamodb.model.PutItemRequest
import com.amazonaws.services.dynamodb.model.AttributeValue
import com.amazonaws.services.dynamodb.model.DeleteTableRequest
import collection.JavaConversions._
import java.util.HashSet
import com.amazonaws.services.dynamodb.model.UpdateTableRequest
import edu.berkeley.cs.amplab.carat.s3.S3Encoder

object DynamoDbEncoder {
  val dd = new AmazonDynamoDBClient(S3Encoder.cred)

  // For putting data:
  val prob = "prob"
  val probNeg = "probNeg"
  val xmax = "xmax"
  val distanceField = "distance"
  val apps = "reportedApps"

  /**
   * Put a new entry into a table that uses only a HashKey.
   * For `bugsTable`, use `putBug()`.
   */
  def put(table: String, keyName: String, keyValue: String,
    maxX: Double,
    prob1: Seq[(Int, Double)], prob2: Seq[(Int, Double)],
    distance: Double,
    ev:Double, evNeg: Double,
    uuidApps: Seq[String] = new HashSet[String].toSeq) {
    if (uuidApps.size > 0)
      put(table, (keyName, keyValue), (xmax, maxX),
        (prob, prob1.map(x => { x._1 + ";" + x._2 })),
        (probNeg, prob2.map(x => { x._1 + ";" + x._2 })),
        (distanceField, distance),
        (expectedValue, ev),
        (expectedValueNeg, evNeg),
        (apps, uuidApps))
    else
      put(table, (keyName, keyValue), (xmax, maxX),
        (prob, prob1.map(x => { x._1 + ";" + x._2 })),
        (probNeg, prob2.map(x => { x._1 + ";" + x._2 })),
        (distanceField, distance),
        (expectedValue, ev),
        (expectedValueNeg, evNeg))
  }

  /**
   * Function that makes it easy to use the ridiculous DynamoDB put API.
   */
  def put(table: String, vals: (String, Any)*) = {
    val map = getMap(vals: _*)
    println("Going to put into " + table + ":\n" + map.mkString("\n"))
    val putReq = new PutItemRequest(table, map)
    safePut(putReq)
  }

  
  /**
   * Function for putting already proper Maps into DynamoDb.
   */
  def putItems(table: String, stuff: java.util.List[java.util.Map[String, AttributeValue]]) = {
    for (map <- stuff) {
      println("Going to put into " + table + ":\n" + map.mkString("\n"))
      val putReq = new PutItemRequest(table, map)
      safePut(putReq)
    }
  }
  /**
   * Function for putting a ready Map into DynamoDb.
   */
  def putItem(table: String, map: java.util.Map[String, AttributeValue]) = {
    println("Going to put into " + table + ":\n" + map.mkString("\n"))
    val putReq = new PutItemRequest(table, map)
    safePut(putReq)
  }
  
  // max allowed write caps per sec
  val capMax = 1400.0
  // write caps used
  var capUsedPerSec = 0.0
  // time write caps were used in
  var lastReq = 0L
  // last request write caps used
  var lastCap = 0.0
  
  
  def safePut(req: PutItemRequest) = {
    val now = System.currentTimeMillis
    if (now - lastReq > 1000){
      // allowed, and
      capUsedPerSec = 0
      lastReq = now
    }else{
      // less than a second elapsed
      if (lastCap > capMax){
        println("Error: lastCap=%s is greater than max=%s".format(lastCap, capMax))
        // invariant fail: do unsafe puts
        capUsedPerSec = 0
        lastReq = now
      }else {
        // wait for a bit
        if (capUsedPerSec + lastCap > capMax) {
          wait(now - lastReq)
          capUsedPerSec = 0
          lastReq = now
        }/* else: within same sec,
        but not past cap limit;
        do not reset capUsedPerSec, do not wait */
      }
    }
    /* Now we either waited, reset capUsedPerSec and lastReq, or did nothing.*/
    val res = dd.putItem(req)
    lastCap = res.getConsumedCapacityUnits()
    capUsedPerSec += lastCap
    println("Put used %s cap, %s cap left".format(lastCap, (capMax - capUsedPerSec)))
  }

  /**
   * Helper function used by put(table, vals).
   * Constructs Maps to be put into a table from a variable number of (String, Any) - pairs.
   */
  def getMap(vals: (String, Any)*) = {
    val map:java.util.Map[String, AttributeValue] = new java.util.HashMap[String, AttributeValue]()
    for (k <- vals) {
        map.put(k._1, toAttributeValue(k._2))
    }
    map
  }

  def toAttributeValue(thing: Any) = {
    if (thing.isInstanceOf[Double]
      || thing.isInstanceOf[Int]
      || thing.isInstanceOf[Long]
      || thing.isInstanceOf[Float]
      || thing.isInstanceOf[Short])
      new AttributeValue().withN(thing + "")
    else if (thing.isInstanceOf[Seq[String]])
      new AttributeValue().withSS(thing.asInstanceOf[Seq[String]])
    else
      new AttributeValue(thing + "")
  }
  
  def convertToMap[T](vals: Seq[(String, T)]) = {
    val map:java.util.Map[String, T] = new java.util.HashMap[String, T]()
     for (k <- vals) {
       map.put(k._1, k._2)
     }
    map
  }

  /**
   * Returns only a single value for an AttributeValue object.
   * May lose data if an AttributeValue has multiple values in it.
   */
  def fromAttributeValue(thing: AttributeValue) = {
    val n = thing.getN()
    val s = thing.getS()
    val ns = thing.getNS()
    val ss = thing.getSS()
    if (n != null)
      ("N", n)
    else if (s != null)
      ("S", s)
    else if (ns != null)
      ("NS", ns)
    else if (ss != null)
      ("SS", ss)
    else
      (null, null)
  }

  /**
   * Put a new entry into `bugsTable`.
   */
  def putBug(table: String, keyNames: (String, String), keyValues: (String, String),
    maxX: Double, prob1: Seq[(Int, Double)], prob2: Seq[(Int, Double)],
    distance: Double, ev:Double, evNeg:Double) {
    put(table, (keyNames._1, keyValues._1), (keyNames._2, keyValues._2), (xmax, maxX),
      (prob, prob1.map(x => { x._1 + ";" + x._2 })),
      (probNeg, prob2.map(x => { x._1 + ";" + x._2 })),
      (distanceField, distance),
      (expectedValue, ev),
      (expectedValueNeg, evNeg))
  }

  /**
   * Test program. describes tables.
   *
   */
  def main(args: Array[String]) {
    /*val tables = dd.listTables().getTableNames()
    S3Decoder.printList(tables)
    val it = tables.iterator()
    while (it.hasNext) {
      val t = it.next
      val getReq = new DescribeTableRequest()
      val desc = dd.describeTable(getReq.withTableName(t))
      println(desc.toString())
      //val item = dd.getItem(new GetItemRequest("carat.latestbugs", new Key(new AttributeValue("85")))).getItem()
      //println("Item: " + item.mkString("\n"))
    }*/
    if (args != null){
      if (args.length == 1){
        if (args(0) == "updateThroughput")
          updateTableThroughput(resultsTable, osTable, modelsTable, hogsTable, bugsTable, similarsTable)
        if (args(0) == "clearTablesDangerous")
          clearTables()
      }
    }
  }

  def updateTableThroughput(tables: String*) {
    for (t <- tables) {
      var READ = 60L
      var WRITE = 60L
      val v = dd.describeTable(new DescribeTableRequest().withTableName(t))
      val tp = v.getTable().getProvisionedThroughput()
      val rd = tp.getReadCapacityUnits()
      val wr = tp.getWriteCapacityUnits()
      if (rd < READ || wr < WRITE) {
        if (rd*2 < READ){
          READ = rd*2
          println("Warning: only increasing read cap of " + t + " to "+ (rd*2) +" due to DynamoDb limits.")
        }
         if (wr*2 < WRITE){
          WRITE = wr*2
          println("Warning: only increasing write cap of " + t + " to "+ (wr*2) +" due to DynamoDb limits.")
        }        
        val k = new UpdateTableRequest().withTableName(t).withProvisionedThroughput(new ProvisionedThroughput().withReadCapacityUnits(READ).withWriteCapacityUnits(WRITE))
        val upd = dd.updateTable(k)
        // apparently only one table can be updated "simultaneously" whatever that means.
        Thread.sleep(40000)
      }else{
        println("Doing nothing, current tp for " + t +" is: " + rd + ", " + wr)
      }
    }
  }
  
  /**
   * Dangerous. Destroys and recreates tables.
   */
  def clearTables() {
    var del = new DeleteTableRequest(resultsTable)
    dd.deleteTable(del)
    del = new DeleteTableRequest(osTable)
    dd.deleteTable(del)
    del = new DeleteTableRequest(modelsTable)
    dd.deleteTable(del)
    del = new DeleteTableRequest(hogsTable)
    dd.deleteTable(del)
    del = new DeleteTableRequest(bugsTable)
    dd.deleteTable(del)
    del = new DeleteTableRequest(similarsTable)
    dd.deleteTable(del)
    // Sleep for 40 secs...
    Thread.sleep(40000)

    createResultsTable()
    createOsTable()
    createModelsTable()
    createHogsTable()
    createBugsTable()
    createSimilarsTable()
  }

  def createResultsTable() {
    val getKey = new KeySchemaElement()
    val ks = getKey.withAttributeName(resultKey)
    ks.setAttributeType("S")
    // will only have current
    val req = new CreateTableRequest(resultsTable, new KeySchema(ks))
    req.setProvisionedThroughput(new ProvisionedThroughput().withReadCapacityUnits(30).withWriteCapacityUnits(20))
    dd.createTable(req)
  }
  
   def createSimilarsTable() {
    val getKey = new KeySchemaElement()
    val ks = getKey.withAttributeName(similarKey)
    ks.setAttributeType("S")
    // will only have current
    val req = new CreateTableRequest(similarsTable, new KeySchema(ks))
    req.setProvisionedThroughput(new ProvisionedThroughput().withReadCapacityUnits(30).withWriteCapacityUnits(20))
    dd.createTable(req)
  }

  def createOsTable() {
    val getKey = new KeySchemaElement()
    val ks = getKey.withAttributeName(osKey)
    ks.setAttributeType("S")
    // will only have current
    val req = new CreateTableRequest(osTable, new KeySchema(ks))
    req.setProvisionedThroughput(new ProvisionedThroughput().withReadCapacityUnits(30).withWriteCapacityUnits(20))
    dd.createTable(req)
  }

  def createModelsTable() {
    val getKey = new KeySchemaElement()
    val ks = getKey.withAttributeName(modelKey)
    ks.setAttributeType("S")
    // will only have current
    val req = new CreateTableRequest(modelsTable, new KeySchema(ks))
    req.setProvisionedThroughput(new ProvisionedThroughput().withReadCapacityUnits(30).withWriteCapacityUnits(20))
    dd.createTable(req)
  }

  def createBugsTable() {
    var getKey = new KeySchemaElement()
    val ks = getKey.withAttributeName(resultKey)
    ks.setAttributeType("S")
    getKey = new KeySchemaElement()
    val rk = getKey.withAttributeName(hogKey)
    rk.setAttributeType("S")
    // will only have current
    val req = new CreateTableRequest(bugsTable, new KeySchema(ks).withRangeKeyElement(rk))
    req.setProvisionedThroughput(new ProvisionedThroughput().withReadCapacityUnits(30).withWriteCapacityUnits(20))
    dd.createTable(req)
  }

  def createHogsTable() {
    val getKey = new KeySchemaElement()
    val ks = getKey.withAttributeName(hogKey)
    ks.setAttributeType("S")
    // will only have current
    val req = new CreateTableRequest(hogsTable, new KeySchema(ks))
    req.setProvisionedThroughput(new ProvisionedThroughput().withReadCapacityUnits(30).withWriteCapacityUnits(20))
    dd.createTable(req)
  }
}
