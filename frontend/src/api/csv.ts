import axiosInstance from './axiosInstance'

export const uploadCsv = (file: FormData) =>
  axiosInstance.post('/api/csv/upload', file, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
