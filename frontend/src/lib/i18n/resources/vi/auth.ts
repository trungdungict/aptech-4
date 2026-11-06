export const viAuth = {
  login: {
    title: 'Đăng nhập',
    subtitle: 'Dùng tài khoản khách Aptech-4. Thông tin demo lấy từ seed, không phải form này.',
    email: 'Email',
    password: 'Mật khẩu',
    submit: 'Đăng nhập',
    submitting: 'Đang đăng nhập…',
    noAccount: 'Chưa có tài khoản?',
    createOne: 'Tạo tài khoản',
    unknownError: 'Không đăng nhập được. Vui lòng thử lại.',
  },
  register: {
    title: 'Tạo tài khoản',
    subtitle: 'Đăng ký tài khoản khách. Vai trò CUSTOMER do máy chủ gán — không chọn trên form.',
    email: 'Email',
    fullName: 'Họ và tên',
    password: 'Mật khẩu',
    submit: 'Tạo tài khoản',
    submitting: 'Đang tạo tài khoản…',
    hasAccount: 'Đã có tài khoản?',
    signIn: 'Đăng nhập',
    unknownError: 'Không đăng ký được. Vui lòng thử lại.',
  },
  requireAuth: {
    checking: 'Đang kiểm tra phiên…',
  },
} as const
