package parameters

import play.api.mvc.Request
import play.api.mvc.AnyContent
import play.api.libs.json.JsObject
import play.api.libs.json.Json

case class AggregateTermVectorProcessingParameters(prefix: String = "", suffix: String = "")(implicit request: Request[AnyContent]) {
  private val p = request.body.asFormUrlEncoded.getOrElse(request.queryString)
  private val smoothingOpt = p.get(prefix+"smoothing"+suffix).map(_(0).toDouble)
  /** Laplace smoothing to use */
  val smoothing = smoothingOpt.getOrElse(2.0)
  private val sumScalingStringOpt = p.get(prefix+"sumScaling"+suffix).map(v => v(0).toUpperCase)
  private val sumScalingString = sumScalingStringOpt.getOrElse("TTF")
  /** sum scaling to use */
  val sumScaling = SumScaling.get(sumScalingString, smoothing)
  private val minSumFreqOpt = p.get(prefix+"minSumFreq"+suffix).map(_(0).toInt)
  /** minimum sum frequency of term to filter resulting term vector */
  val minSumFreq: Int = minSumFreqOpt.getOrElse(1)
  private val maxSumFreqOpt = p.get(prefix+"maxSumFreq"+suffix).map(_(0).toInt)
  /** maximum sum frequency of term to filter resulting term vector */
  val maxSumFreq: Int = maxSumFreqOpt.getOrElse(Int.MaxValue)
  final def matches(sumFreq: Int): Boolean = {
    (minSumFreq == 1 && maxSumFreq == Int.MaxValue) || (minSumFreq <= sumFreq && maxSumFreq >= sumFreq)
  }
  val limitOpt = p.get(prefix+"limit"+suffix).map(_(0).toInt)
  val limit: Int = limitOpt.getOrElse(20)
  private val dimensionsOpt = p.get(prefix+"dimensions"+suffix).map(_(0).toInt)
  /** amount of dimensions for dimensionally reduced term vector coordinates */
  val dimensions: Int = dimensionsOpt.getOrElse(0)
  
  private val dimensionalityReductionOpt = p.get(prefix+"dimReduct"+suffix).map(v => DimensionalityReduction.withName(v(0).toUpperCase))
  val dimensionalityReduction: DimensionalityReduction = dimensionalityReductionOpt.getOrElse(DimensionalityReduction.SMDS)
  
  val tsnePerplexity: Double = 20.0
  val tsneMaxIter: Int = 1000
  val tsneUsePCA: Boolean = true
  val tsneTheta: Double = 0.5
  
  private val distanceOpt = p.get("distance").map(v => DistanceMetric.withName(v(0).toUpperCase))
  /** distance metric used for term vector comparisons */
  val distance: DistanceMetric = distanceOpt.getOrElse(DistanceMetric.COSINE)
  
  /** are vectors normalized before distance calculation? */
  val normalize: Boolean = p.get("normalize").exists(v => v(0)=="" || v(0).toBoolean)
  /** are vectors centered before distance calculation? */
  val center: Boolean = p.get("center").exists(v => v(0)=="" || v(0).toBoolean)
  /** does distance calculation only operate on dimensions where both vectors have a value? (does not apply to DICE/JACCARD metrics)*/
  val onlyCommon: Boolean = p.get("onlyCommon").exists(v => v(0)=="" || v(0).toBoolean)
  
  def toJson(): JsObject = Json.obj(prefix+"normalize"+suffix->normalize,prefix+"center"+suffix->center,prefix+"onlyCommon"+suffix->onlyCommon,prefix+"dimReduct"+suffix->dimensionalityReduction.entryName,prefix+"smoothing"+suffix->smoothing,prefix+"sumScaling"+suffix->sumScalingString,prefix+"minSumFreq"+suffix->minSumFreq,prefix+"maxSumFreq"+suffix->maxSumFreq,prefix+"limit"+suffix->limit, prefix+"dimensions"+suffix->dimensions, prefix+"distance"+suffix->distance.entryName) 
}