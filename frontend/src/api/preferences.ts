import axiosInstance from './axiosInstance'

export const setOnlineDays = (data: unknown) =>
  axiosInstance.post('/api/preferences/online-days', data)

export const getOnlineDays = () =>
  axiosInstance.get('/api/preferences/online-days')
