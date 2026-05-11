import axiosInstance from './axiosInstance'
import type { PersonalTaskCreateRequestDTO } from '../types/personalTask'

export const createPersonalTask = (data: PersonalTaskCreateRequestDTO) =>
  axiosInstance.post('/api/personal-tasks', data)

export const getMyPersonalTasks = () =>
  axiosInstance.get('/api/personal-tasks/my')

export const updatePersonalTaskStatus = (id: number, data: unknown) =>
  axiosInstance.patch(`/api/personal-tasks/${id}/status`, data)

export const deletePersonalTask = (id: number) =>
  axiosInstance.delete(`/api/personal-tasks/${id}`)

export const updatePersonalTask = (id: number, data: unknown) =>
  axiosInstance.put(`/api/personal-tasks/${id}`, data)
