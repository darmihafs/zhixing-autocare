import http from './http'

export const authApi = {
  login: (data) => http.post('/auth/login', data),
  register: (data) => http.post('/auth/register', data),
  me: () => http.get('/user/me'),
  updateProfile: (data) => http.put('/user/me', data),
  exitImpersonation: () => http.post('/user/impersonation/exit')
}

export const productApi = {
  categories: () => http.get('/product/category/tree'),
  page: (params) => http.get('/product/page', { params }),
  detail: (id) => http.get(`/product/${id}`)
}

export const shopApi = {
  page: (params) => http.get('/shop/page', { params }),
  search: (params) => http.get('/shop/search', { params }),
  detail: (id) => http.get(`/shop/${id}`),
  services: (params) => http.get('/shop/service/page', { params }),
  serviceSearch: (params) => http.get('/shop/service/search', { params })
}

export const orderApi = {
  create: (data) => http.post('/order', data),
  state: (orderNo) => http.get(`/order/state/${orderNo}`),
  page: (params) => http.get('/order/page', { params }),
  detail: (orderNo) => http.get(`/order/${orderNo}`),
  cancel: (orderNo) => http.put(`/order/${orderNo}/cancel`),
  complete: (orderNo) => http.put(`/order/${orderNo}/complete`),
  demoDeliver: (orderNo) => http.post(`/order/${orderNo}/demo-deliver`)
}

export const cartApi = {
  list: () => http.get('/cart/list'),
  count: () => http.get('/cart/count'),
  add: (data) => http.post('/cart', data),
  update: (id, data) => http.put(`/cart/${id}`, data),
  remove: (id) => http.delete(`/cart/${id}`),
  selectAll: (selected) => http.put('/cart/select-all', null, { params: { selected } }),
  removeSelected: () => http.delete('/cart/selected')
}

export const followApi = {
  follow: (targetType, targetId) => http.post(`/follow/${targetType}/${targetId}`),
  unfollow: (targetType, targetId) => http.delete(`/follow/${targetType}/${targetId}`),
  status: (targetType, targetId) => http.get('/follow/status', { params: { targetType, targetId } }),
  list: (targetType) => http.get('/follow/list', { params: { targetType } })
}

export const payApi = {
  create: (orderNo) => http.post('/pay/create', { orderNo }),
  mock: (outTradeNo) => http.post(`/pay/${outTradeNo}/mock`),
  detail: (outTradeNo) => http.get(`/pay/${outTradeNo}`)
}

export const likeApi = {
  like: (targetType, targetId) => http.post(`/like/${targetType}/${targetId}`),
  unlike: (targetType, targetId) => http.delete(`/like/${targetType}/${targetId}`),
  status: (targetType, targetId) => http.get('/like/status', { params: { targetType, targetId } }),
  list: (targetType) => http.get('/like/list', { params: { targetType } })
}

export const reviewApi = {
  itemPage: (itemType, itemId, params) =>
    http.get(`/review/item/${itemType}/${itemId}`, { params }),
  itemStats: (itemType, itemId) => http.get(`/review/item/${itemType}/${itemId}/stats`),
  shopPage: (shopId, params) => http.get(`/review/shop/${shopId}`, { params }),
  shopStats: (shopId) => http.get(`/review/shop/${shopId}/stats`),
  detail: (id) => http.get(`/review/detail/${id}`),
  create: (data) => http.post('/review', data),
  mine: (params) => http.get('/review/my', { params }),
  upload: (file) => {
    const form = new FormData()
    form.append('file', file)
    return http.post('/review/upload', form, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  },
  reply: (id, reply) => http.put(`/review/${id}/reply`, { reply })
}

export const adminApi = {
  shopPage: (params) => http.get('/manage/shop/page', { params }),
  createShop: (data) => http.post('/manage/shop', data),
  updateShop: (id, data) => http.put(`/manage/shop/${id}`, data),
  shopStatus: (id, status) => http.put(`/manage/shop/${id}/status`, null, { params: { status } }),
  servicePage: (params) => http.get('/manage/shop/service/page', { params }),
  createService: (data) => http.post('/manage/shop/service', data),
  updateService: (id, data) => http.put(`/manage/shop/service/${id}`, data),
  serviceStatus: (id, status) =>
    http.put(`/manage/shop/service/${id}/status`, null, { params: { status } }),
  orderPage: (params) => http.get('/order/manage/page', { params }),
  reviewPage: (params) => http.get('/review/manage/page', { params }),
  userPage: (params) => http.get('/user/manage/page', { params }),
  impersonate: (id) => http.post(`/user/manage/${id}/impersonate`),
  accept: (orderNo) => http.post(`/order/manage/${orderNo}/accept`),
  deliver: (orderNo) => http.post(`/order/manage/${orderNo}/deliver`)
}
