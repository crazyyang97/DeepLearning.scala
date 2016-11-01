package com.thoughtworks.deepLearning
package array2D.ast

import cats._
import cats.implicits._
import com.thoughtworks.deepLearning.Ast._
import com.thoughtworks.deepLearning.Batch._
import org.nd4j.linalg.api.ndarray.INDArray
import org.nd4s.Implicits._
import com.thoughtworks.deepLearning.array2D.utilities.Array2DSemigroupBatch

/**
  * @author 杨博 (Yang Bo) &lt;pop.atry@gmail.com&gt;
  */
final case class MultiplyDouble[Input0 <: Batch](
    leftOperand: WidenAst[Input0, WidenBatch[Eval[INDArray], Eval[INDArray]]],
    rightOperand: WidenAst[Input0, WidenBatch[Eval[scala.Double], Eval[scala.Double]]]
) extends Ast
    with Cached {

  protected final class SharedBatch private[deepLearning](override val input: Input0,
                                    upstream1: WidenBatch[Eval[INDArray], Eval[INDArray]],
                                    upstream2: WidenBatch[Eval[scala.Double], Eval[scala.Double]])
      extends Array2DSemigroupBatch
      with SemigroupBatch {
    val value = upstream1.value.map2(upstream2.value)(_ * _).memoize

    type Input >: Input0

    override protected def closeUpstreams(): Unit = {
      upstream1.close()
      upstream2.close()
    }

    override protected def rawBackward(outputDelta: Eval[INDArray]): Unit = {
      val a = upstream1.value
      val b = upstream2.value

      val aDelta = outputDelta.map2(b)(_ * _).memoize
      upstream1.backward(aDelta)
      val bDelta = outputDelta
        .map2(a) { (outputDeltaValue, aValue) =>
          (aValue * outputDeltaValue).sumT
        }
        .memoize
      upstream2.backward(bDelta)
    }
  }

  type Input = Input0

  override protected def rawForward(input: Input): SharedBatch = {
    new SharedBatch(input, leftOperand.forward(input), rightOperand.forward(input))
  }
}
