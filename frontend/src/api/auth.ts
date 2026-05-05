import axiosInstance from './axiosInstance'
import type { LoginRequest } from '../types/auth'

export const login = (data: LoginRequest) =>
  axiosInstance.post('/auth/login', data)

export const logout = () => axiosInstance.post('/auth/logout')

export const registerCompany = (data: unknown) =>
  axiosInstance.post('/auth/register-company', data)

export const registerInvited = (data: unknown) =>
  axiosInstance.post('/auth/register-invited', data)

export const verifyOtp = (data: unknown) =>
  axiosInstance.post('/auth/verify', data)
