export interface RequestSubmissionDTO {
  reason: string
  requestedDate: string
  requestType: string
}

export interface RequestResponseDTO {
  id: number
  reason: string
  requestedDate: string
  requestType: string
  status: string
}
