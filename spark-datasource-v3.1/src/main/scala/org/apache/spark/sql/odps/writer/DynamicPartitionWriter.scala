/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.spark.sql.odps.writer

import com.aliyun.odps.Column
import com.aliyun.odps.cupid.table.v1.writer.{FileWriter, FileWriterBuilder, WriteSessionInfo}
import com.aliyun.odps.data.ArrayRecord
import org.apache.spark.sql.catalyst.InternalRow
import org.apache.spark.sql.connector.write.{DataWriter, WriterCommitMessage}
import org.apache.spark.sql.odps.converter.TypesConverter

import scala.collection.JavaConverters._

/**
  * @author renxiang
  * @date 2021-12-23
  */
class DynamicPartitionWriter(partitionId: Int,
                             converters: List[Object => AnyRef],
                             writeSessionInfo: WriteSessionInfo,
                             columns: java.util.List[Column],
                             partitions: java.util.List[Column]
                            ) extends DataWriter[InternalRow] {
  private var _currentPartitionSpec: Option[java.util.Map[String, String]] = None
  private var _currentWriter: FileWriter[ArrayRecord] = null
  private val _commitMsg = new SparkCommitMessage

  private val _arrayRecord: ArrayRecord = {
    val columnArray = columns.toArray(new Array[Column](0))
    new ArrayRecord(columnArray)
  }

  override def write(row: InternalRow): Unit = {
    newWriterIfNewPartition(row)
    _currentWriter.write(transform(row))
  }

  override def commit(): WriterCommitMessage = {
    if (_currentWriter != null) {
      val msg = _currentWriter.commitWithResult()
      _currentWriter.close()
      _commitMsg.addMsg(msg)
    }

    _commitMsg
  }

  override def abort(): Unit = {
    if (_currentWriter != null) {
      _currentWriter.close()
    }
  }

  override def close(): Unit = {
    if (_currentWriter != null) {
      _currentWriter.close()
    }
  }

  private def transform(row: InternalRow): ArrayRecord = {
    var i = 0
    while (i < converters.length) {
      val value = if (row.isNullAt(i)) {
        null
      } else {
        val sparkType = TypesConverter.odpsType2SparkType(columns.get(i).getTypeInfo)
        converters(i)(row.get(i, sparkType))
      }
      _arrayRecord.set(i, value)
      i += 1
    }
    _arrayRecord
  }

  private def newWriterIfNewPartition(row: InternalRow) : FileWriter[ArrayRecord] = {
    val partitionSpec = extractPartitionSpec(row)

    var shouldNewWriter = _currentPartitionSpec.isEmpty
    if (_currentPartitionSpec.isDefined && !mapEquals(partitionSpec, _currentPartitionSpec.get)) {
      val msg = _currentWriter.commitWithResult()
      _currentWriter.close()
      _commitMsg.addMsg(msg)
      shouldNewWriter = true
    }

    if (shouldNewWriter) {
      _currentPartitionSpec = Option(partitionSpec)
      _currentWriter = new FileWriterBuilder(writeSessionInfo, partitionId)
        .partitionSpec(partitionSpec)
        .buildRecordWriter()
    }

    _currentWriter
  }

  private def mapEquals(srcM: java.util.Map[String, String], destM: java.util.Map[String, String]): Boolean = {
    srcM.asScala.forall{case (k, v) => destM.containsKey(k) && destM.get(k) == v}
  }

  private def extractPartitionSpec(row: InternalRow): java.util.Map[String, String] = {
    val baseIdx = columns.size()

    partitions.asScala
      .zipWithIndex
      .map{case (f, idx) => {
        val rowIdx = baseIdx + idx
        val sparkType = TypesConverter.odpsType2SparkType(f.getTypeInfo)
        val sparkData = row.get(rowIdx, sparkType)

        val name = f.getName
        val typeinfo = f.getTypeInfo
        val odpsData = TypesConverter.sparkData2OdpsData(typeinfo)(sparkData).asInstanceOf[String]

        (name, odpsData)
      }}
      .toMap
      .asJava
  }
}
