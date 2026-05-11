export interface MeetingDTO {
  id: number
  title: string
  date: string
  startTime: string
  endTime: string
  teamId: number
}

export interface MeetingRequestDTO {
  title: string
  date: string
  startTime: string
  endTime: string
  teamId: number
}
