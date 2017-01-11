package parameters

import play.api.mvc.Request
import play.api.mvc.AnyContent

case class AggregateTermVectorProcessingParameters(prefix: String = "", suffix: String = "")(implicit request: Request[AnyContent]) {
  private val p = request.body.asFormUrlEncoded.getOrElse(request.queryString)
  private val sumScalingOpt = p.get(prefix+"sumScaling"+suffix).map(v => SumScaling.withName(v(0).toUpperCase))
  val sumScaling: SumScaling = sumScalingOpt.getOrElse(SumScaling.TTF)
  private val minSumFreqOpt = p.get(prefix+"minSumFreq"+suffix).map(_(0).toInt)
  /** minimum sum frequency of term to filter resulting term vector */
  val minSumFreq: Int = minSumFreqOpt.getOrElse(1)
  private val maxSumFreqOpt = p.get(prefix+"maxSumFreq"+suffix).map(_(0).toInt)
  /** maximum sum frequency of term to filter resulting term vector */
  val maxSumFreq: Int = maxSumFreqOpt.getOrElse(Int.MaxValue)
  final def matches(sumFreq: Int): Boolean = {
    (minSumFreq == 1 && maxSumFreq == Int.MaxValue) || (minSumFreq <= sumFreq && maxSumFreq >= sumFreq)
  }
  private val mdsDimensionsOpt = p.get("mdsDimensions").map(_(0).toInt)
  /** amount of dimensions for dimensionally reduced term vector coordinates */
  val mdsDimensions: Int = mdsDimensionsOpt.getOrElse(-1)    
  private val distanceOpt = p.get("distance").map(v => DistanceMetric.withName(v(0).toUpperCase))
  /** distance metric used for term vector comparisons */
  val distance: DistanceMetric = distanceOpt.getOrElse(DistanceMetric.COSINE)
  val defined: Boolean = sumScalingOpt.isDefined || minSumFreqOpt.isDefined || maxSumFreqOpt.isDefined|| mdsDimensionsOpt.isDefined || distanceOpt.isDefined
  override def toString() = s"${prefix}sumScaling$suffix:$sumScaling, ${prefix}minSumFreq$suffix:$minSumFreq, ${prefix}maxSumFreq$suffix:$maxSumFreq, ${prefix}mdsDimensions$suffix:$mdsDimensions, ${prefix}distance$suffix:$distance"
}