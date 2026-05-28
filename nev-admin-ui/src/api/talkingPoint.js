import api from '../composables/useApi'

export const getVersions = (talkingPointId) =>
  api.get(`/api/v1/talking-point/${talkingPointId}/versions`)

export const getCurrentVersion = (talkingPointId) =>
  api.get(`/api/v1/talking-point/${talkingPointId}/current`)

export const rollback = (talkingPointId, version) =>
  api.post(`/api/v1/talking-point/${talkingPointId}/rollback/${version}`)
