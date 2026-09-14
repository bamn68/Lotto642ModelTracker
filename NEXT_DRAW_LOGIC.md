# Next Draw logic

The Home screen must derive NEXT DRAW from the latest verified Lotto 6/42 result.

1. Find the most recent draw with `verified=true`.
2. Advance that date to the next Tuesday, Thursday, or Saturday using `RecommendationEngine.nextDrawDate()`.
3. Display that calculated date as NEXT DRAW.
4. A model run target date is only a fallback when there is no verified result at all.

This prevents a completed model-run target date from remaining displayed as the next draw after its result has already been verified.
