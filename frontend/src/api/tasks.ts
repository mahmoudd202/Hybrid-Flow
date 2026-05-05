import axiosInstance from './axiosInstance'
import type { TaskCreateRequestDTO } from '../types/task'

export const createTask = (data: TaskCreateRequestDTO) =>
  axiosInstance.post('/api/tasks', data)

export const deleteTask = (taskId: number) =>
  axiosInstance.delete(`/api/tasks/${taskId}`)

export const updateTask = (taskId: number, data: unknown) =>
  axiosInstance.put(`/api/tasks/${taskId}`, data)

export const getManagerCreatedTasks = () =>
  axiosInstance.get('/api/tasks/manager/created')

export const getMyAssignments = () =>
  axiosInstance.get('/api/tasks/my-assignments')

export const getTaskAssignments = (taskId: number) =>
  axiosInstance.get(`/api/tasks/${taskId}/assignments`)

export const updateAssignmentStatus = (assignmentId: number, data: unknown) =>
  axiosInstance.patch(`/api/tasks/assignments/${assignmentId}/status`, data)
