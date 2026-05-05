import axiosInstance from './axiosInstance'

export const sendInvitation = (data: unknown) =>
  axiosInstance.post('/api/invitations/send', data)
