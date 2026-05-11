import axiosInstance from './axiosInstance'
import type { MeetingRequestDTO } from '../types/meeting'

export const createMeeting = (data: MeetingRequestDTO) =>
  axiosInstance.post('/api/meetings', data)

export const deleteMeeting = (meetingId: number) =>
  axiosInstance.delete(`/api/meetings/${meetingId}`)

export const updateMeeting = (meetingId: number, data: unknown) =>
  axiosInstance.put(`/api/meetings/${meetingId}`, data)

export const getMyMeetingSchedule = () =>
  axiosInstance.get('/api/meetings/my-schedule')

export const getTeamMeetings = (teamId: number) =>
  axiosInstance.get(`/api/meetings/team/${teamId}`)
