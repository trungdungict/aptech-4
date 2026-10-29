import type { ChatLanguage } from '../types'

export type ChatCopy = {
  title: string
  subtitle: string
  placeholder: string
  send: string
  clear: string
  language: string
  english: string
  vietnamese: string
  greeting: string
  loading: string
  retry: string
  disabled: string
  unavailable: string
  empty: string
  interrupted: string
  retryLast: string
}

const EN: ChatCopy = {
  title: 'Aptech Assistant',
  subtitle: 'Ask about orders and products. Answers use live store data.',
  placeholder: 'Ask a question…',
  send: 'Send',
  clear: 'Clear chat',
  language: 'Language',
  english: 'English',
  vietnamese: 'Vietnamese',
  greeting: 'Hello! I can help with your orders, product search, and checkout questions.',
  loading: 'Thinking…',
  retry: 'Retry',
  disabled: 'AI assistant is disabled on this environment.',
  unavailable: 'The AI model is unavailable. Try again later or ask an operator to start Ollama.',
  empty: 'No messages yet.',
  interrupted: 'Response interrupted before completion. Partial text is shown above.',
  retryLast: 'Retry last question',
}

const VI: ChatCopy = {
  title: 'Trợ lý Aptech',
  subtitle: 'Hỏi về đơn hàng và sản phẩm. Câu trả lời dựa trên dữ liệu thật.',
  placeholder: 'Nhập câu hỏi…',
  send: 'Gửi',
  clear: 'Xóa hội thoại',
  language: 'Ngôn ngữ',
  english: 'English',
  vietnamese: 'Tiếng Việt',
  greeting: 'Xin chào! Tôi có thể giúp về đơn hàng, tìm sản phẩm và hướng dẫn thanh toán.',
  loading: 'Đang suy nghĩ…',
  retry: 'Thử lại',
  disabled: 'Trợ lý AI đang tắt trên môi trường này.',
  unavailable: 'Mô hình AI không khả dụng. Thử lại sau hoặc nhờ vận hành khởi động Ollama.',
  empty: 'Chưa có tin nhắn.',
  interrupted: 'Phản hồi bị gián đoạn trước khi hoàn tất. Phần đã nhận vẫn hiển thị ở trên.',
  retryLast: 'Gửi lại câu hỏi trước',
}

export function chatCopy(language: ChatLanguage): ChatCopy {
  return language === 'VI' ? VI : EN
}

export function defaultSuggestedPrompts(language: ChatLanguage, admin: boolean): string[] {
  if (admin) {
    return language === 'VI'
      ? [
          'Có bao nhiêu đơn đang chờ xử lý?',
          'Sản phẩm nào sắp hết hàng?',
          'Tóm tắt doanh thu 30 ngày gần đây.',
        ]
      : [
          'How many orders need processing?',
          'Which products are low on stock?',
          'Summarize sales for the last 30 days.',
        ]
  }
  return language === 'VI'
    ? [
        'Đơn hàng gần nhất của tôi đang ở trạng thái nào?',
        'Tìm sản phẩm còn hàng dưới 2 triệu.',
        'Tôi checkout thế nào?',
      ]
    : [
        'What is the status of my latest order?',
        'Show available products under 2000000.',
        'How do I complete checkout?',
      ]
}
