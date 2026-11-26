const BASE = 'http://localhost:8080/api'
const stamp = Math.floor(Date.now() / 1000)
const username = `p0e2e${stamp}`
const password = 'Test123456'
const phone = `139${String(stamp).slice(-8)}`
const phoneUpdated = `138${String(stamp).slice(-8)}`

const results = []
let token = ''
let user = null
let shopId = 1
let serviceItemId = 2
let cancelServiceItemId = 1
let productA = 1
let productB = 2
let productOrderNo = ''
let serviceOrderNo = ''
let cancelOrderNo = ''
let reviewId = null
let baselineStock = {}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

async function call(path, options = {}) {
  const headers = {}
  if (options.token !== undefined ? options.token : token) {
    headers.Authorization = `Bearer ${options.token !== undefined ? options.token : token}`
  }
  if (options.body !== undefined) headers['Content-Type'] = 'application/json'
  const response = await fetch(`${BASE}${path}`, {
    method: options.method || 'GET',
    headers,
    body: options.body === undefined ? undefined : JSON.stringify(options.body)
  })
  const text = await response.text()
  let body
  try {
    body = JSON.parse(text)
  } catch {
    body = text
  }
  return { status: response.status, body, data: body?.data }
}

function check(name, condition, detail = '') {
  results.push({ name, ok: Boolean(condition), detail })
  console.log(`${condition ? 'PASS' : 'FAIL'}  ${name}${detail ? ' :: ' + detail : ''}`)
  if (!condition) throw new Error(`断言失败: ${name} ${detail}`)
}

async function waitOrder(orderNo) {
  for (let i = 0; i < 60; i += 1) {
    const state = await call(`/order/state/${orderNo}`)
    check(`订单状态查询可用 ${orderNo}`, state.status === 200, `http=${state.status}`)
    if (['CREATED', 'FAILED', 'NOT_FOUND'].includes(state.data?.state)) return state.data
    await sleep(500)
  }
  throw new Error(`订单 ${orderNo} 创建超时`)
}

async function waitStatus(orderNo, expected, label) {
  for (let i = 0; i < 40; i += 1) {
    const detail = await call(`/order/${orderNo}`)
    if (detail.data?.status === expected) return detail.data
    await sleep(500)
  }
  throw new Error(`${label}: 订单 ${orderNo} 未到达状态 ${expected}`)
}

async function payOrder(orderNo) {
  const created = await call('/pay/create', { method: 'POST', body: { orderNo } })
  check(`创建支付单 ${orderNo}`, created.body?.code === 200 && created.data?.outTradeNo,
    `code=${created.body?.code} msg=${created.body?.message}`)
  const outTradeNo = created.data.outTradeNo
  const paid = await call(`/pay/${outTradeNo}/mock`, { method: 'POST' })
  check(`模拟支付成功 ${outTradeNo}`, paid.body?.code === 200 && paid.data?.status === 1,
    `code=${paid.body?.code} status=${paid.data?.status}`)
  return outTradeNo
}

async function main() {
  const register = await call('/auth/register', {
    method: 'POST',
    token: null,
    body: { username, password, nickname: 'P0 E2E', phone }
  })
  check('注册临时验收账号', register.body?.code === 200 && register.data?.id,
    `code=${register.body?.code} msg=${register.body?.message}`)
  user = register.data

  const login = await call('/auth/login', {
    method: 'POST',
    token: null,
    body: { username, password }
  })
  check('登录获取 JWT', login.body?.code === 200 && login.data?.token,
    `code=${login.body?.code}`)
  token = login.data.token

  const unauthorized = await call('/cart/list', { token: null })
  check('购物车接口强制鉴权（无 Token 401）',
    unauthorized.status === 401 || unauthorized.body?.code === 401,
    `http=${unauthorized.status} code=${unauthorized.body?.code}`)

  const productOne = await call(`/product/${productA}`)
  const productTwo = await call(`/product/${productB}`)
  check('读取商品基准价与库存', productOne.data?.id && productTwo.data?.id)
  baselineStock = {
    [productA]: { stock: productOne.data.stock, sales: productOne.data.sales },
    [productB]: { stock: productTwo.data.stock, sales: productTwo.data.sales }
  }

  const addA1 = await call('/cart', { method: 'POST', body: { productId: productA, quantity: 1 } })
  const addA2 = await call('/cart', { method: 'POST', body: { productId: productA, quantity: 1 } })
  const addB = await call('/cart', { method: 'POST', body: { productId: productB, quantity: 3 } })
  check('加购三件商品', [addA1, addA2, addB].every((item) => item.body?.code === 200))

  let cartList = await call('/cart/list')
  const cartA = cartList.data.find((item) => String(item.productId) === String(productA))
  check('同商品重复加购合并数量', cartA?.quantity === 2, `quantity=${cartA?.quantity}`)
  check('购物车返回当前价格与库存', cartA?.price > 0 && cartA?.stock > 0)

  await call(`/cart/${cartA.id}`, { method: 'PUT', body: { selected: 0 } })
  const afterDeselect = await call('/cart/list')
  const deselectedA = afterDeselect.data.find((item) => String(item.productId) === String(productA))
  check('取消勾选状态持久化', deselectedA?.selected === false)
  const countAll = await call('/cart/count')
  check('购物车角标统计全部条目数量', Number(countAll.data) === 5, `count=${countAll.data}`)

  await call('/cart/select-all?selected=true', { method: 'PUT', body: null, token })
  const selectedList = await call('/cart/list')
  check('全选恢复所有可用商品勾选', selectedList.data.every((item) => item.selected))

  const profile = await call('/user/me', {
    method: 'PUT',
    body: { nickname: 'P0 E2E 验收', phone: phoneUpdated, avatar: '', gender: 1 }
  })
  check('个人资料修改成功', profile.body?.code === 200 && profile.data?.nickname === 'P0 E2E 验收',
    `code=${profile.body?.code} msg=${profile.body?.message}`)
  const me = await call('/user/me')
  check('个人资料持久化并回读一致', me.data?.phone === phoneUpdated && me.data?.gender === 1)

  const follow = await call(`/follow/1/${shopId}`, { method: 'POST' })
  const followStatus = await call(`/follow/status?targetType=1&targetId=${shopId}`)
  const followList = await call('/follow/list?targetType=1')
  check('关注门店闭环', follow.body?.code === 200 && followStatus.data === true
    && followList.data.map(String).includes(String(shopId)))
  await call(`/follow/1/${shopId}`, { method: 'DELETE' })
  const unfollowStatus = await call(`/follow/status?targetType=1&targetId=${shopId}`)
  check('取消关注生效', unfollowStatus.data === false)
  await call(`/follow/1/${shopId}`, { method: 'POST' })

  const likeProduct = await call(`/like/3/${productA}`, { method: 'POST' })
  const likeProductStatus = await call(`/like/status?targetType=3&targetId=${productA}`)
  const likeProductList = await call('/like/list?targetType=3')
  check('点赞商品闭环', likeProduct.body?.code === 200 && likeProductStatus.data === true
    && likeProductList.data.map(String).includes(String(productA)))

  const orderCreate = await call('/order', {
    method: 'POST',
    body: {
      orderType: 1,
      items: [
        { productId: productA, quantity: 2 },
        { productId: productB, quantity: 3 }
      ]
    }
  })
  check('购物车合并下单提交成功', orderCreate.body?.code === 200 && orderCreate.data?.orderNo,
    `code=${orderCreate.body?.code} msg=${orderCreate.body?.message}`)
  productOrderNo = orderCreate.data.orderNo
  const productState = await waitOrder(productOrderNo)
  check('多商品订单异步创建成功', productState.state === 'CREATED', `state=${productState.state}`)

  const productDetail = await call(`/order/${productOrderNo}`)
  check('商品订单金额按当前价格汇总',
    Number(productDetail.data.totalAmount) === 2 * Number(productOne.data.price)
      + 3 * Number(productTwo.data.price),
    `total=${productDetail.data.totalAmount}`)
  check('商品订单包含两件商品', productDetail.data.items.length === 2)

  const afterOrderStock = await call('/product/1')
  check('商品订单扣减库存', afterOrderStock.data.stock === baselineStock[productA].stock - 2,
    `stock=${afterOrderStock.data.stock}`)

  await call('/cart/selected', { method: 'DELETE' })
  const cartAfter = await call('/cart/list')
  check('下单后清除已勾选购物车条目', cartAfter.data.length === 0)

  await payOrder(productOrderNo)
  await waitStatus(productOrderNo, 10, '商品订单支付')
  check('支付回调推进商品订单状态', true)
  const delivered = await call(`/order/${productOrderNo}/demo-deliver`, { method: 'POST' })
  check('模拟商家发货进入服务中', delivered.data?.status === 20 || delivered.body?.code === 200)
  const completed = await call(`/order/${productOrderNo}/complete`, { method: 'PUT' })
  check('确认完成商品订单', completed.body?.code === 200)
  await waitStatus(productOrderNo, 30, '商品订单完成')

  const productReview = await call('/review', {
    method: 'POST',
    body: { orderNo: productOrderNo, itemId: productA, score: 5, content: 'P0 E2E 商品评价' }
  })
  check('商品订单发布评价', productReview.body?.code === 200 && productReview.data?.itemType === 1,
    `code=${productReview.body?.code} itemType=${productReview.data?.itemType}`)
  const productReviewId = productReview.data?.id

  const likeReview = await call(`/like/1/${productReviewId}`, { method: 'POST' })
  const likeReviewStatus = await call(`/like/status?targetType=1&targetId=${productReviewId}`)
  check('点赞评价闭环', likeReview.body?.code === 200 && likeReviewStatus.data === true)

  const tomorrow = new Date(Date.now() + 24 * 3600 * 1000)
  const yyyy = tomorrow.getFullYear()
  const mm = String(tomorrow.getMonth() + 1).padStart(2, '0')
  const dd = String(tomorrow.getDate()).padStart(2, '0')
  const appointmentTime = `${yyyy}-${mm}-${dd}T10:00:00`
  const serviceCreate = await call('/order', {
    method: 'POST',
    body: {
      orderType: 2,
      shopId,
      appointmentTime,
      appointmentContact: 'P0 E2E',
      appointmentPhone: phone,
      items: [{ serviceItemId, quantity: 1 }],
      remark: 'P0 E2E 服务预约'
    }
  })
  check('门店服务预约下单提交成功', serviceCreate.body?.code === 200 && serviceCreate.data?.orderNo,
    `code=${serviceCreate.body?.code} msg=${serviceCreate.body?.message}`)
  serviceOrderNo = serviceCreate.data.orderNo
  const serviceState = await waitOrder(serviceOrderNo)
  check('服务订单异步创建成功', serviceState.state === 'CREATED', `state=${serviceState.state}`)

  const serviceDetail = await call(`/order/${serviceOrderNo}`)
  check('服务订单类型/门店/预约信息落库',
    serviceDetail.data.orderType === 2
      && String(serviceDetail.data.shopId) === String(shopId)
      && serviceDetail.data.appointmentTime
      && serviceDetail.data.appointmentContact === 'P0 E2E'
      && serviceDetail.data.appointmentPhone === phone,
    JSON.stringify({
      orderType: serviceDetail.data.orderType,
      shopId: serviceDetail.data.shopId,
      appointmentTime: serviceDetail.data.appointmentTime
    }))
  check('服务订单明细指向服务项目', String(serviceDetail.data.items[0]?.itemId) === String(serviceItemId),
    `itemId=${serviceDetail.data.items[0]?.itemId}`)
  check('服务订单金额取服务当前价', Number(serviceDetail.data.totalAmount) === 39.00,
    `total=${serviceDetail.data.totalAmount}`)

  await payOrder(serviceOrderNo)
  await waitStatus(serviceOrderNo, 10, '服务订单支付')
  await call(`/order/${serviceOrderNo}/demo-deliver`, { method: 'POST' })
  await waitStatus(serviceOrderNo, 20, '服务订单接单')
  check('服务订单模拟门店接单', true)
  await call(`/order/${serviceOrderNo}/complete`, { method: 'PUT' })
  await waitStatus(serviceOrderNo, 30, '服务订单完成')
  check('服务订单完成', true)

  const serviceReview = await call('/review', {
    method: 'POST',
    body: { orderNo: serviceOrderNo, itemId: serviceItemId, score: 4, content: 'P0 E2E 服务评价' }
  })
  check('服务订单发布评价且 itemType=2',
    serviceReview.body?.code === 200 && serviceReview.data?.itemType === 2,
    `code=${serviceReview.body?.code} itemType=${serviceReview.data?.itemType}`)
  reviewId = serviceReview.data?.id

  const shopReviews = await call(`/review/shop/${shopId}?pageNum=1&pageSize=20`)
  check('门店评价列表包含服务评价',
    shopReviews.data?.records?.some((item) => String(item.id) === String(reviewId)))
  const serviceReviewPage = await call(`/review/item/2/${serviceItemId}?pageNum=1&pageSize=20`)
  check('服务项目评价分页可查',
    serviceReviewPage.data?.records?.some((item) => String(item.id) === String(reviewId)))

  const stockBeforeCancel = await call('/product/1')
  const cancelCreate = await call('/order', {
    method: 'POST',
    body: {
      orderType: 2,
      shopId,
      appointmentTime,
      appointmentContact: 'P0 E2E',
      appointmentPhone: phone,
      items: [{ serviceItemId: cancelServiceItemId, quantity: 1 }]
    }
  })
  cancelOrderNo = cancelCreate.data.orderNo
  await waitOrder(cancelOrderNo)
  const cancelDetail = await call(`/order/${cancelOrderNo}`)
  const stockAfterServiceCreate = await call('/product/1')
  check('创建服务订单不扣减商品库存',
    stockAfterServiceCreate.data.stock === stockBeforeCancel.data.stock,
    `before=${stockBeforeCancel.data.stock} after=${stockAfterServiceCreate.data.stock}`)
  const cancelled = await call(`/order/${cancelOrderNo}/cancel`, { method: 'PUT' })
  check('服务订单可取消', cancelled.body?.code === 200)
  const stockAfterCancel = await call('/product/1')
  check('取消服务订单不回补商品库存（不应变化）',
    stockAfterCancel.data.stock === stockBeforeCancel.data.stock,
    `before=${stockBeforeCancel.data.stock} after=${stockAfterCancel.data.stock}`)

  const ordersPage = await call('/order/page?pageNum=1&pageSize=20')
  check('订单列表包含商品订单与服务订单', ordersPage.data?.records?.some((item) => item.orderType === 1)
    && ordersPage.data?.records?.some((item) => item.orderType === 2))

  console.log('\nSUMMARY')
  console.log(JSON.stringify({
    username,
    password,
    userId: user.id,
    productA,
    productB,
    baselineStock,
    productOrderNo,
    serviceOrderNo,
    cancelOrderNo,
    productReviewId,
    serviceReviewId: reviewId,
    passed: results.filter((item) => item.ok).length,
    failed: results.filter((item) => !item.ok).length
  }, null, 2))
}

main().catch((error) => {
  console.error('\nE2E FAILED:', error.message)
  console.log(JSON.stringify({ username, password, results }, null, 2))
  process.exitCode = 1
})
