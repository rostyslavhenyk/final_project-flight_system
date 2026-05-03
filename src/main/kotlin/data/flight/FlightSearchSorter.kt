package data.flight

internal object FlightSearchSorter {
    fun sortRecords(
        rows: List<FlightSearchRepository.FlightScheduleRecord>,
        sort: FlightSearchRepository.FlightSortOption,
        ascending: Boolean,
    ): List<FlightSearchRepository.FlightScheduleRecord> {
        val comparator =
            when (sort) {
                FlightSearchRepository.FlightSortOption.Recommended ->
                    compareBy { row: FlightSearchRepository.FlightScheduleRecord -> row.recommendedRank }
                FlightSearchRepository.FlightSortOption.DepartureTime ->
                    compareBy { row: FlightSearchRepository.FlightScheduleRecord -> row.departTime }
                FlightSearchRepository.FlightSortOption.ArrivalTime ->
                    compareBy(
                        { row: FlightSearchRepository.FlightScheduleRecord -> row.arrivalOffsetDays },
                        { row -> row.arrivalTime },
                    )
                FlightSearchRepository.FlightSortOption.Duration ->
                    compareBy { row: FlightSearchRepository.FlightScheduleRecord -> row.durationMinutes }
                FlightSearchRepository.FlightSortOption.Fare ->
                    compareBy { row: FlightSearchRepository.FlightScheduleRecord -> row.priceLight }
                FlightSearchRepository.FlightSortOption.Stops ->
                    compareBy { row: FlightSearchRepository.FlightScheduleRecord -> row.stops }
            }
        return if (ascending || sort == FlightSearchRepository.FlightSortOption.Recommended) {
            rows.sortedWith(comparator)
        } else {
            rows.sortedWith(comparator.reversed())
        }
    }
}
