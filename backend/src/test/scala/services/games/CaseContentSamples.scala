package services.games

import services.cases.objects.CaseContent

private[games] object CaseContentSamples:
  def coverageOnly(content: CaseContent): CaseContent =
    content.copy(conclusion=content.conclusion.map(_.copy(evaluation=None)))
