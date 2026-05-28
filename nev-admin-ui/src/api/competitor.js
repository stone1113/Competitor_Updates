import api from '../composables/useApi'

export const getDashboard = (date) =>
  api.get('/api/v1/competitor-dashboard', { params: { date } })

export const getByBrand = (brand, date) =>
  api.get(`/api/v1/competitor-dashboard/brand/${brand}`, { params: { date } })
