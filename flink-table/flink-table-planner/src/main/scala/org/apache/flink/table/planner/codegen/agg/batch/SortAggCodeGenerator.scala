/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.flink.table.planner.codegen.agg.batch

import org.apache.flink.streaming.api.operators.OneInputStreamOperator
import org.apache.flink.table.data.RowData
import org.apache.flink.table.data.binary.BinaryRowData
import org.apache.flink.table.data.utils.JoinedRowData
import org.apache.flink.table.functions.AggregateFunction
import org.apache.flink.table.planner.codegen.{CodeGeneratorContext, CodeGenUtils, ProjectionCodeGenerator}
import org.apache.flink.table.planner.codegen.OperatorCodeGenerator.generateCollect
import org.apache.flink.table.planner.plan.utils.AggregateInfoList
import org.apache.flink.table.planner.typeutils.RowTypeUtils
import org.apache.flink.table.runtime.generated.GeneratedOperator
import org.apache.flink.table.runtime.operators.TableStreamOperator
import org.apache.flink.table.types.logical.RowType

import org.apache.calcite.tools.RelBuilder

/**
 * Sort aggregation code generator to deal with all aggregate functions with keys. It require input
 * in keys order.
 */
object SortAggCodeGenerator {

  def genWithKeys(
      ctx: CodeGeneratorContext,
      builder: RelBuilder,
      aggInfoList: AggregateInfoList,
      inputType: RowType,
      outputType: RowType,
      grouping: Array[Int],
      auxGrouping: Array[Int],
      isMerge: Boolean,
      isFinal: Boolean): GeneratedOperator[OneInputStreamOperator[RowData, RowData]] = {

    // prepare for aggregation
    val aggInfos = aggInfoList.aggInfos
    aggInfos
      .map(_.function)
      .filter(_.isInstanceOf[AggregateFunction[_, _]])
      .map(ctx.addReusableFunction(_))
    val functionIdentifiers = AggCodeGenHelper.getFunctionIdentifiers(aggInfos)
    val aggBufferPrefix = "sort"
    val aggBufferNames = AggCodeGenHelper.getAggBufferNames(aggBufferPrefix, auxGrouping, aggInfos)
    val aggBufferTypes = AggCodeGenHelper.getAggBufferTypes(inputType, auxGrouping, aggInfos)

    val inputTerm = CodeGenUtils.DEFAULT_INPUT1_TERM
    val lastKeyTerm = "lastKey"
    val currentKeyTerm = "currentKey"
    val currentKeyWriterTerm = "currentKeyWriter"

    val groupKeyRowType = RowTypeUtils.projectRowType(inputType, grouping)
    val keyProjectionCode = ProjectionCodeGenerator
      .generateProjectionExpression(
        ctx,
        inputType,
        groupKeyRowType,
        grouping,
        inputTerm = inputTerm,
        outRecordTerm = currentKeyTerm,
        outRecordWriterTerm = currentKeyWriterTerm)
      .code

    val keyNotEquals = AggCodeGenHelper.genGroupKeyChangedCheckCode(currentKeyTerm, lastKeyTerm)

    val (initAggBufferCode, doAggregateCode, aggOutputExpr) = AggCodeGenHelper.genSortAggCodes(
      isMerge,
      isFinal,
      ctx,
      builder,
      grouping,
      auxGrouping,
      aggInfos,
      functionIdentifiers,
      inputTerm,
      inputType,
      aggBufferPrefix,
      aggBufferNames,
      aggBufferTypes,
      outputType
    )

    val joinedRow = "joinedRow"
    ctx.addReusableOutputRecord(outputType, classOf[JoinedRowData], joinedRow)
    val binaryRow = classOf[BinaryRowData].getName
    ctx.addReusableMember(s"$binaryRow $lastKeyTerm = null;")

    val processCode =
      s"""
         |hasInput = true;
         |${ctx.reuseInputUnboxingCode(inputTerm)}
         |
         |// project key from input
         |$keyProjectionCode
         |if ($lastKeyTerm == null) {
         |  $lastKeyTerm = $currentKeyTerm.copy();
         |
         |  // init agg buffer
         |  $initAggBufferCode
         |} else if ($keyNotEquals) {
         |
         |  // write output
         |  ${aggOutputExpr.code}
         |
         |  ${generateCollect(s"$joinedRow.replace($lastKeyTerm, ${aggOutputExpr.resultTerm})")}
         |
         |  $lastKeyTerm = $currentKeyTerm.copy();
         |
         |  // init agg buffer
         |  $initAggBufferCode
         |}
         |
         |// do doAggregateCode
         |$doAggregateCode
         |""".stripMargin.trim

    val endInputCode =
      s"""
         |if (hasInput) {
         |  // write last output
         |  ${aggOutputExpr.code}
         |  ${generateCollect(s"$joinedRow.replace($lastKeyTerm, ${aggOutputExpr.resultTerm})")}
         |}
       """.stripMargin

    val className = if (isFinal) "SortAggregateWithKeys" else "LocalSortAggregateWithKeys"
    AggCodeGenHelper.generateOperator(
      ctx,
      className,
      classOf[TableStreamOperator[RowData]].getCanonicalName,
      processCode,
      endInputCode,
      inputType)
  }
}
