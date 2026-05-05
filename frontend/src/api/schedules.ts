import axiosInstance from './axiosInstance'

export const getMySchedule = () => axiosInstance.get('/api/schedules/me')

export const getEmployeeSchedule = (employeeId: number) =>
  axiosInstance.get(`/api/schedules/employees/${employeeId}`)

export const getTeamSchedule = (teamId: number) =>
  axiosInstance.get(`/api/schedules/team/${teamId}`)

export const getCompanySchedule = () =>
  axiosInstance.get('/api/schedules/company')

export const getOfficeSchedule = (officeId: number) =>
  axiosInstance.get(`/api/schedules/office/${officeId}`)
