import { useCallback, useEffect, useRef, useState } from 'react'
import type { Mode } from '@/api/types'

// Web Speech API types
interface SpeechRecognitionEvent extends Event {
  results: SpeechRecognitionResultList
  resultIndex: number
}

interface SpeechRecognitionErrorEvent extends Event {
  error: string
  message?: string
}

interface SpeechRecognitionInstance extends EventTarget {
  continuous: boolean
  interimResults: boolean
  lang: string
  start: () => void
  stop: () => void
  abort: () => void
  onresult: ((event: SpeechRecognitionEvent) => void) | null
  onerror: ((event: SpeechRecognitionErrorEvent) => void) | null
  onend: (() => void) | null
}

declare global {
  interface Window {
    SpeechRecognition?: new () => SpeechRecognitionInstance
    webkitSpeechRecognition?: new () => SpeechRecognitionInstance
  }
}

export function useVoice({
  mode,
  onTranscript,
}: {
  mode: Mode
  onTranscript: (text: string) => void
}) {
  const [listening, setListening] = useState(false)
  const [speaking, setSpeaking] = useState(false)
  const [supported, setSupported] = useState(true)
  const recognitionRef = useRef<SpeechRecognitionInstance | null>(null)
  const onTranscriptRef = useRef(onTranscript)
  onTranscriptRef.current = onTranscript

  // Check support on mount
  useEffect(() => {
    const hasSTT = typeof window !== 'undefined' && !!(window.SpeechRecognition || window.webkitSpeechRecognition)
    const hasTTS = typeof window !== 'undefined' && 'speechSynthesis' in window
    setSupported(hasSTT || hasTTS)
  }, [])

  // Initialize Speech Recognition
  useEffect(() => {
    if (typeof window === 'undefined') return
    const SpeechRec = window.SpeechRecognition || window.webkitSpeechRecognition
    if (!SpeechRec) return

    const recognition = new SpeechRec()
    recognition.continuous = true
    recognition.interimResults = true
    recognition.lang = 'vi-VN'

    recognition.onresult = (event: SpeechRecognitionEvent) => {
      let finalTranscript = ''
      for (let i = event.resultIndex; i < event.results.length; ++i) {
        if (event.results[i].isFinal) {
          finalTranscript += event.results[i][0].transcript + ' '
        }
      }
      if (finalTranscript.trim()) {
        onTranscriptRef.current(finalTranscript)
      }
    }

    recognition.onerror = (event: SpeechRecognitionErrorEvent) => {
      if (event.error !== 'no-speech') {
        setListening(false)
      }
    }

    recognition.onend = () => {
      setListening(false)
    }

    recognitionRef.current = recognition

    return () => {
      try {
        recognition.abort()
      } catch {}
    }
  }, [])

  const startListening = useCallback(() => {
    if (!recognitionRef.current) return
    try {
      recognitionRef.current.start()
      setListening(true)
    } catch {
      setListening(true)
    }
  }, [])

  const stopListening = useCallback(() => {
    if (!recognitionRef.current) return
    try {
      recognitionRef.current.stop()
    } catch {}
    setListening(false)
  }, [])

  const toggleListening = useCallback(() => {
    if (listening) {
      stopListening()
    } else {
      startListening()
    }
  }, [listening, startListening, stopListening])

  // Text-To-Speech
  const speak = useCallback(
    (rawText: string) => {
      if (typeof window === 'undefined' || !('speechSynthesis' in window)) return
      if (mode !== 'voice_voice') return

      window.speechSynthesis.cancel()

      // Clean markdown tags like **, ##, ```, etc.
      const clean = rawText
        .replace(/```[\s\S]*?```/g, '')
        .replace(/`.*?`/g, '')
        .replace(/[#*_~>]/g, '')
        .replace(/https?:\/\/\S+/g, '')
        .trim()

      if (!clean) return

      const utter = new SpeechSynthesisUtterance(clean)
      utter.lang = 'vi-VN'
      utter.rate = 1.0
      utter.pitch = 1.0

      const voices = window.speechSynthesis.getVoices()
      const viVoice = voices.find((v) => v.lang.startsWith('vi') || v.lang.includes('VN'))
      if (viVoice) utter.voice = viVoice

      utter.onstart = () => setSpeaking(true)
      utter.onend = () => setSpeaking(false)
      utter.onerror = () => setSpeaking(false)

      window.speechSynthesis.speak(utter)
    },
    [mode],
  )

  const stopSpeaking = useCallback(() => {
    if (typeof window !== 'undefined' && 'speechSynthesis' in window) {
      window.speechSynthesis.cancel()
      setSpeaking(false)
    }
  }, [])

  // Cancel speaking if mode changes or unmounts
  useEffect(() => {
    return () => {
      if (typeof window !== 'undefined' && 'speechSynthesis' in window) {
        window.speechSynthesis.cancel()
      }
    }
  }, [])

  return {
    listening,
    speaking,
    supported,
    startListening,
    stopListening,
    toggleListening,
    speak,
    stopSpeaking,
  }
}
