export interface TaskResponseDTO {
  id: number
  title: string
  description: string
  status: string
  dueDate: string
  teamId: number
}

export interface TaskCreateRequestDTO {
  title: string
  description: string
  dueDate: string
  teamId: number
}

export interface TaskAssignmentResponseDTO {
  assignmentId: number
  taskId: number
  title: string
  status: string
}
