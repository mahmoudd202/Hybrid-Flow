export interface PersonalTaskResponseDTO {
  id: number
  title: string
  description: string
  status: string
  dueDate: string
}

export interface PersonalTaskCreateRequestDTO {
  title: string
  description: string
  dueDate: string
}
