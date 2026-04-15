import { USER_AUTH, SYS_BUTTON_AUTH } from '@/store/mutation-types'

function readPermissionList(key) {
  try {
    return JSON.parse(sessionStorage.getItem(key) || '[]')
  } catch (e) {
    return []
  }
}

export function hasButtonPermission(action, type = '1') {
  if (!action) {
    return true
  }
  const userAuthList = readPermissionList(USER_AUTH)
  const allAuthList = readPermissionList(SYS_BUTTON_AUTH)
  const globalMatches = allAuthList.filter(item => item && item.action === action && item.type === type)
  if (globalMatches.some(item => item.status === '0')) {
    return true
  }
  return userAuthList.some(item => item && item.action === action && item.type === type)
}

export function hasAnyButtonPermission(actions, type = '1') {
  if (!Array.isArray(actions) || actions.length === 0) {
    return true
  }
  return actions.some(action => hasButtonPermission(action, type))
}
