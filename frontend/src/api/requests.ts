import axiosInstance from './axiosInstance'
import type { RequestSubmissionDTO } from '../types/request'

export const submitRequest = (data: RequestSubmissionDTO) =>
  axiosInstance.post('/api/requests', data)

export const getMyRequests = () =>
  axiosInstance.get('/api/requests/my-requests')

export const deleteRequest = (requestId: number) =>
  axiosInstance.delete(`/api/requests/${requestId}`)

export const getPendingRequests = () =>
  axiosInstance.get('/api/requests/pending')

export const approveRequest = (requestId: number) =>
  axiosInstance.patch(`/api/requests/${requestId}/approve`)

export const rejectRequest = (requestId: number) =>
  axiosInstance.patch(`/api/requests/${requestId}/reject`)
