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
import org.apache.flink.table.functions.AggregateFunction
import org.apache.flink.table.planner.codegen.{CodeGeneratorContext, CodeGenUtils}
import org.apache.flink.table.planner.codegen.OperatorCodeGenerator.generateCollect
import org.apache.flink.table.planner.codegen.agg.batch.AggCodeGenHelper.genSortAggCodes
import org.apache.flink.table.planner.plan.utils.AggregateInfoList
import org.apache.flink.table.runtime.generated.GeneratedOperator
import org.apache.flink.table.runtime.operators.TableStreamOperator
import org.apache.flink.table.types.logical.RowType

import org.apache.calcite.tools.RelBuilder

/** Generate a agg operator without keys, auxGrouping must be empty too. */
object AggWithoutKeysCodeGenerator {

  def genWithoutKeys(
      ctx: CodeGeneratorContext,
      builder: RelBuilder,
      aggInfoList: AggregateInfoList,
      inputType: RowType,
      outputType: RowType,
      isMerge: Boolean,
      isFinal: Boolean,
      prefix: String): GeneratedOperator[OneInputStreamOperator[RowData, RowData]] = {

    // prepare for aggregation
    val auxGrouping = Array[Int]()
    val aggInfos = aggInfoList.aggInfos
    aggInfos
      .map(_.function)
      .filter(_.isInstanceOf[AggregateFunction[_, _]])
      .map(ctx.addReusableFunction(_))
    val functionIdentifiers = AggCodeGenHelper.getFunctionIdentifiers(aggInfos)
    val aggBufferPrefix = "hash"
    val aggBufferNames = AggCodeGenHelper.getAggBufferNames(aggBufferPrefix, auxGrouping, aggInfos)
    val aggBufferTypes = AggCodeGenHelper.getAggBufferTypes(inputType, auxGrouping, aggInfos)

    val inputTerm = CodeGenUtils.DEFAULT_INPUT1_TERM

    val (initAggBufferCode, doAggregateCode, aggOutputExpr) = genSortAggCodes(
      isMerge,
      isFinal,
      ctx,
      builder,
      Array(),
      Array(),
      aggInfos,
      functionIdentifiers,
      inputTerm,
      inputType,
      aggBufferPrefix,
      aggBufferNames,
      aggBufferTypes,
      outputType)

    val processCode =
      s"""
         |if (!hasInput) {
         |  hasInput = true;
         |  // init agg buffer
         |  $initAggBufferCode
         |}
         |
         |${ctx.reuseInputUnboxingCode()}
         |$doAggregateCode
         |""".stripMargin.trim

    // if the input is empty in final phase, we should output default values
    val endInputCode = if (isFinal) {
      s"""
         |if (!hasInput) {
         |  $initAggBufferCode
         |}
         |${aggOutputExpr.code}
         |${generateCollect(aggOutputExpr.resultTerm)}
         |""".stripMargin
    } else {
      s"""
         |if (hasInput) {
         |  ${aggOutputExpr.code}
         |  ${generateCollect(aggOutputExpr.resultTerm)}
         |}""".stripMargin
    }

    val className =
      if (isFinal) s"${prefix}AggregateWithoutKeys" else s"Local${prefix}AggregateWithoutKeys"
    AggCodeGenHelper.generateOperator(
      ctx,
      className,
      classOf[TableStreamOperator[RowData]].getCanonicalName,
      processCode,
      endInputCode,
      inputType)
  }
}
