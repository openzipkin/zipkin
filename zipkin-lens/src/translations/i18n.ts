import i18n from 'i18next'
import {initReactI18next} from "react-i18next";
import LanguageDetector from 'i18next-browser-languagedetector'
import {messages as messages_fr} from './fr/messages'
import {messages as messages_es} from './es/messages'

import {messages as messages_zh} from './zh-cn/messages'


const resources = {
  es: {
    translation: messages_fr
  },
  fr:{
    translation: messages_es
  },
  zh_cn: {
    translation:messages_zh
  }
}

i18n
  .use(LanguageDetector)
  .use(initReactI18next)
  .init(
    {
      resources
    }
  )

export default i18n
