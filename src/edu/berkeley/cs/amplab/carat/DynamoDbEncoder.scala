package edu.berkeley.cs.amplab.carat

import com.amazonaws.services.dynamodb.AmazonDynamoDBClient
import com.amazonaws.services.dynamodb.model.CreateTableRequest
import com.amazonaws.services.dynamodb.model.DescribeTableRequest
import com.amazonaws.services.dynamodb.model.KeySchemaElement
import com.amazonaws.services.dynamodb.model.KeySchema
import com.amazonaws.services.dynamodb.model.ProvisionedThroughput
import com.amazonaws.services.dynamodb.model.PutItemRequest
import com.amazonaws.services.dynamodb.model.AttributeValue
import com.amazonaws.services.dynamodb.model.DeleteTableRequest
import com.amazonaws.services.dynamodb.model.UpdateTableRequest
import collection.JavaConversions._
import java.util.HashSet

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
    distance: Double, uuidApps: Seq[String] = new HashSet[String].toSeq) {
    if (uuidApps.size > 0)
      put(table, (keyName, keyValue), (xmax, maxX),
        (prob, prob1.map(x => { x._1 + ";" + x._2 })),
        (probNeg, prob2.map(x => { x._1 + ";" + x._2 })),
        (distanceField, distance),
        (apps, uuidApps))
    else
      put(table, (keyName, keyValue), (xmax, maxX),
        (prob, prob1.map(x => { x._1 + ";" + x._2 })),
        (probNeg, prob2.map(x => { x._1 + ";" + x._2 })),
        (distanceField, distance))
  }

  /**
   * Function that makes it easy to use the ridiculous DynamoDB put API.
   */
  def put(table: String, vals: (String, Any)*) = {
    val map = getMap(vals: _*)
    println("Going to put into " + table + ":\n" + map.mkString("\n"))
    val putReq = new PutItemRequest(table, map)
    dd.putItem(putReq)
  }

  /**
   * Helper function used by put(table, vals).
   * Constructs Maps to be put into a table from a variable number of (String, Any) - pairs.
   */
  def getMap(vals: (String, Any)*) = {
    val map:java.util.Map[String, AttributeValue] = new java.util.HashMap[String, AttributeValue]()
    for (k <- vals) {
      if (k._2.isInstanceOf[Double]
        || k._2.isInstanceOf[Int]
        || k._2.isInstanceOf[Long]
        || k._2.isInstanceOf[Float]
        || k._2.isInstanceOf[Short])
        map.put(k._1, new AttributeValue().withN(k._2 + ""))
      else if (k._2.isInstanceOf[Seq[String]])
        map.put(k._1, new AttributeValue().withSS(k._2.asInstanceOf[Seq[String]]))
      else
        map.put(k._1, new AttributeValue(k._2 + ""))
    }
    map
  }
  
  def convertToMap[T](vals: Seq[(String, T)]) = {
    val map:java.util.Map[String, T] = new java.util.HashMap[String, T]()
     for (k <- vals) {
       map.put(k._1, k._2)
     }
    map
  }

  /**
   * Put a new entry into `bugsTable`.
   */
  def putBug(table: String, keyNames: (String, String), keyValues: (String, String),
    maxX: Double, prob1: Seq[(Int, Double)], prob2: Seq[(Int, Double)],
    distance: Double) {
    put(table, (keyNames._1, keyValues._1), (keyNames._2, keyValues._2), (xmax, maxX),
      (prob, prob1.map(x => { x._1 + ";" + x._2 })),
      (probNeg, prob2.map(x => { x._1 + ";" + x._2 })),
      (distanceField, distance))
  }

  /**
   * Test program. describes tables.
   *
   */
  def main(args: Array[String]) {
    val tables = dd.listTables().getTableNames()
    S3Decoder.printList(tables)
    val it = tables.iterator()
    while (it.hasNext) {
      val t = it.next
      val getReq = new DescribeTableRequest()
      val desc = dd.describeTable(getReq.withTableName(t))
      println(desc.toString())
      //val item = dd.getItem(new GetItemRequest("carat.latestbugs", new Key(new AttributeValue("85")))).getItem()
      //println("Item: " + item.mkString("\n"))
    }
    val k = new UpdateTableRequest().withTableName(samplesTable).withProvisionedThroughput(new ProvisionedThroughput().withReadCapacityUnits(30).withWriteCapacityUnits(30))
    dd.updateTable(k)
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
    del = new DeleteTableRequest(appsTable)
    dd.deleteTable(del)
    del = new DeleteTableRequest(bugsTable)
    dd.deleteTable(del)

    Thread.sleep(5)

    createResultsTable()
    createOsTable()
    createModelsTable()
    createAppsTable()
    createBugsTable()
  }

  def createResultsTable() {
    val getKey = new KeySchemaElement()
    val ks = getKey.withAttributeName(resultKey)
    ks.setAttributeType("S")
    // will only have current
    val req = new CreateTableRequest(resultsTable, new KeySchema(ks))
    req.setProvisionedThroughput(new ProvisionedThroughput().withReadCapacityUnits(30).withWriteCapacityUnits(10))
    dd.createTable(req)
  }

  def createOsTable() {
    val getKey = new KeySchemaElement()
    val ks = getKey.withAttributeName(osKey)
    ks.setAttributeType("S")
    // will only have current
    val req = new CreateTableRequest(osTable, new KeySchema(ks))
    req.setProvisionedThroughput(new ProvisionedThroughput().withReadCapacityUnits(30).withWriteCapacityUnits(10))
    dd.createTable(req)
  }

  def createModelsTable() {
    val getKey = new KeySchemaElement()
    val ks = getKey.withAttributeName(modelKey)
    ks.setAttributeType("S")
    // will only have current
    val req = new CreateTableRequest(modelsTable, new KeySchema(ks))
    req.setProvisionedThroughput(new ProvisionedThroughput().withReadCapacityUnits(30).withWriteCapacityUnits(10))
    dd.createTable(req)
  }

  def createBugsTable() {
    var getKey = new KeySchemaElement()
    val ks = getKey.withAttributeName(resultKey)
    ks.setAttributeType("S")
    getKey = new KeySchemaElement()
    val rk = getKey.withAttributeName(appKey)
    rk.setAttributeType("S")
    // will only have current
    val req = new CreateTableRequest(bugsTable, new KeySchema(ks).withRangeKeyElement(rk))
    req.setProvisionedThroughput(new ProvisionedThroughput().withReadCapacityUnits(30).withWriteCapacityUnits(10))
    dd.createTable(req)
  }

  def createAppsTable() {
    val getKey = new KeySchemaElement()
    val ks = getKey.withAttributeName(appKey)
    ks.setAttributeType("S")
    // will only have current
    val req = new CreateTableRequest(appsTable, new KeySchema(ks))
    req.setProvisionedThroughput(new ProvisionedThroughput().withReadCapacityUnits(30).withWriteCapacityUnits(10))
    dd.createTable(req)
  }
}