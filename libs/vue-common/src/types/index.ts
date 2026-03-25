export interface User {
  id: string
  username: string
  email: string
  role: 'ADMIN' | 'USER' | 'READONLY'
}

export interface ApiError {
  message: string
  code: string
}

export interface ProblemDetail {
  type: string
  title: string
  status: number
  detail?: string
  instance?: string
  errors?: FieldError[]
}

export interface FieldError {
  field: string
  message: string
}
