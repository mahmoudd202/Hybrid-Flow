export interface ScheduleEntryDTO {
  date: string
  workLocation: string
}

export interface ScheduleViewResponseDTO {
  entries: ScheduleEntryDTO[]
}
