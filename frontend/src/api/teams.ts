import axiosInstance from './axiosInstance'
import type { TeamCreateRequestDTO } from '../types/team'

export const createTeam = (data: TeamCreateRequestDTO) =>
  axiosInstance.post('/api/teams', data)
