package parameters

import groovy.lang.{Binding, GroovyClassLoader, Script}
import org.apache.lucene.index.IndexReader
import org.codehaus.groovy.runtime.InvokerHelper
import play.api.libs.json.Json
import play.api.mvc.{AnyContent, Request}
import services.IndexAccess

class LocalTermVectorProcessingParameters(prefix: String = "", suffix: String = "")(implicit request: Request[AnyContent], ia: IndexAccess, queryMetadata: QueryMetadata) {
  import ia.{docFreq, termOrdToTerm, totalTermFreq}
  private val p = request.body.asFormUrlEncoded.getOrElse(request.queryString)
  private val minFreqInDocOpt = p.get(prefix+"minFreqInDoc"+suffix).map(_.head.toLong)
  /** minimum per document frequency of term to be added to the term vector */
  val minFreqInDoc: Long = minFreqInDocOpt.getOrElse(1l)
  private val maxFreqInDocOpt = p.get(prefix+"maxFreqInDoc"+suffix).map(_.head.toLong)
  /** maximum per document frequency of term to be added to the term vector */
  val maxFreqInDoc: Long = maxFreqInDocOpt.getOrElse(Long.MaxValue)
  private def freqInDocMatches(freq: Long): Boolean = freq>=minFreqInDoc && freq<=maxFreqInDoc
  private val localScalingOpt = p.get(prefix+"localScaling"+suffix).map(v => LocalTermVectorScaling.withName(v.head.toUpperCase))
  /** per-doc term vector scaling: absolute, min (w.r.t query term) or flat (1) */
  val localScaling: LocalTermVectorScaling.Value = localScalingOpt.getOrElse(LocalTermVectorScaling.ABSOLUTE)
  private val minTotalTermFreqOpt = p.get(prefix+"minTotalTermFreq"+suffix).map(_.head.toLong)
  /** minimum total term frequency of term to be added to the term vector */
  val minTotalTermFreq: Long = minTotalTermFreqOpt.getOrElse(1l)
  private val maxTotalTermFreqOpt = p.get(prefix+"maxTotalTermFreq"+suffix).map(_.head.toLong)
  /** maximum total term frequency of term to be added to the term vector */
  val maxTotalTermFreq: Long = maxTotalTermFreqOpt.getOrElse(Long.MaxValue)
  private def totalTermFreqMatches(ir: IndexReader, term: Long): Boolean = {
    if (minTotalTermFreq == 1 && maxTotalTermFreq==Long.MaxValue) return true
    val ttfr = totalTermFreq(ir, term)
    ttfr>=minTotalTermFreq && ttfr<=maxTotalTermFreq
  }
  private val minDocFreqOpt = p.get(prefix+"minDocFreq"+suffix).map(_.head.toInt)
  /** minimum total document frequency of term to be added to the term vector */
  val minDocFreq: Int = minDocFreqOpt.getOrElse(1)
  private val maxDocFreqOpt = p.get(prefix+"maxDocFreq"+suffix).map(_.head.toInt)
  /** maximum total document frequency of term to be added to the term vector */
  val maxDocFreq: Int = maxDocFreqOpt.getOrElse(Int.MaxValue)
  private def docFreqMatches(ir: IndexReader, term: Long): Boolean = {
    if (minDocFreq == 1 && maxDocFreq==Int.MaxValue) return true
    val dfr = docFreq(ir, term)
    dfr>=minDocFreq && dfr<=maxDocFreq
  }
  private val minTermLengthOpt = p.get(prefix+"minTermLength"+suffix).map(_.head.toInt)
  /** minimum length of term to be included in the term vector */
  val minTermLength: Int = minTermLengthOpt.getOrElse(1)
  private val maxTermLengthOpt = p.get(prefix+"maxTermLength"+suffix).map(_.head.toInt)
  /** maximum length of term to be included in the term vector */
  val maxTermLength: Int = maxTermLengthOpt.getOrElse(Int.MaxValue)
  private def termLengthMatches(ir: IndexReader, term: Long): Boolean = {
    if (minTermLength == 1 && maxTermLength == Int.MaxValue) return true
    val terms = termOrdToTerm(ir, term)
    terms.length>=minTermLength && terms.length<=maxTermLength
  }
  private val termFilterAsStringOpt = p.get(prefix+"termFilter"+suffix).map(_.head)
  /** Groovy function for filtering terms */
  private val termFilter = termFilterAsStringOpt.map(scriptS => {
    val script = new GroovyClassLoader().parseClass(scriptS)
    new ThreadLocal[Script] {
      override def initialValue(): Script = InvokerHelper.createScript(script, new Binding())
    }
  })
  private val termTransformerAsStringOpt = p.get(prefix+"termTransformer"+suffix).map(_.head)
  /** Groovy function for transforming terms */
  val termTransformer = termTransformerAsStringOpt.map(scriptS => {
    val script = new GroovyClassLoader().parseClass(scriptS)
    new ThreadLocal[Script] {
      override def initialValue(): Script = InvokerHelper.createScript(script, new Binding())
    }
  })
  final def matches(ir: IndexReader, term: Long, freq: Long): Boolean =
    freqInDocMatches(freq) && docFreqMatches(ir,term) && totalTermFreqMatches(ir,term) && termLengthMatches(ir,term) && termFilter.forall(stl => {
      val s = stl.get
      s.getBinding.setProperty("term", termOrdToTerm(ir, term))
      s.getBinding.setProperty("freq", freq)
      s.run().asInstanceOf[Boolean]
    })
  def toJson = Json.obj(
    prefix+"localScaling"+suffix->localScaling,
    prefix+"minTotalTermFreq"+suffix->minTotalTermFreq,
    prefix+"maxTotalTermFreq"+suffix->maxTotalTermFreq,
    prefix+"minDocFreq"+suffix->minDocFreq,
    prefix+"maxDocFreq"+suffix->maxDocFreq,
    prefix+"minFreqInDoc"+suffix -> minFreqInDoc,
    prefix+"maxFreqInDoc"+suffix -> maxFreqInDoc,
    prefix+"minTermLength" + suffix -> minTermLength,
    prefix+"maxTermLength" + suffix -> maxTermLength,
    prefix+"termTransformer"+suffix->termTransformerAsStringOpt,
    prefix+"termFilter"+suffix->termFilterAsStringOpt
  )
  queryMetadata.json = queryMetadata.json ++ toJson
}

