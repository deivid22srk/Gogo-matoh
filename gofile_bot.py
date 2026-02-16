import os
import logging
import requests
from telegram import Update
from telegram.ext import ApplicationBuilder, ContextTypes, MessageHandler, filters, CommandHandler

# Configuração de logging
logging.basicConfig(
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
    level=logging.INFO
)

# --- CONFIGURAÇÕES ---
# Substitua pelo seu Token do Bot do Telegram (obtido no @BotFather)
TELEGRAM_BOT_TOKEN = '8343442752:AAHZbleu8_exX0Oxt5CuTA4L-E6LMxhxfps'

# Opcional: Seu Token da API do Gofile (obtido em https://gofile.io/api)
# Se deixar em branco, o upload será feito como convidado.
GOFILE_API_TOKEN = '' 

# Endpoint de upload do Gofile (São Paulo para melhor latência no Brasil)
GOFILE_UPLOAD_URL = 'https://upload-sa-sao.gofile.io/uploadfile'
# ---------------------

async def start(update: Update, context: ContextTypes.DEFAULT_TYPE):
    """Responde ao comando /start."""
    await update.message.reply_text(
        "Olá! Eu sou o Gofile Uploader Bot.\n\n"
        "Envie-me qualquer arquivo (documento, foto, vídeo) e eu farei o upload para o Gofile.io para você!"
    )

async def handle_document(update: Update, context: ContextTypes.DEFAULT_TYPE):
    """Processa arquivos enviados como documentos ou mídia."""
    message = update.message
    
    # Identifica o arquivo (pode ser documento, foto, vídeo, áudio, etc.)
    file_obj = None
    file_name = "arquivo"
    
    if message.document:
        file_obj = message.document
        file_name = message.document.file_name
    elif message.photo:
        file_obj = message.photo[-1] # Pega a maior resolução
        file_name = f"photo_{file_obj.file_unique_id}.jpg"
    elif message.video:
        file_obj = message.video
        file_name = message.video.file_name or f"video_{file_obj.file_unique_id}.mp4"
    elif message.audio:
        file_obj = message.audio
        file_name = message.audio.file_name or f"audio_{file_obj.file_unique_id}.mp3"
    elif message.voice:
        file_obj = message.voice
        file_name = f"voice_{file_obj.file_unique_id}.ogg"

    if not file_obj:
        return

    status_message = await message.reply_text("📥 Baixando arquivo do Telegram...")

    try:
        # 1. Baixar o arquivo do Telegram
        new_file = await context.bot.get_file(file_obj.file_id)
        file_path = f"temp_{file_name}"
        await new_file.download_to_drive(file_path)

        await status_message.edit_text("📤 Fazendo upload para o Gofile...")

        # 2. Fazer upload para o Gofile
        with open(file_path, 'rb') as f:
            files = {'file': (file_name, f)}
            headers = {}
            if GOFILE_API_TOKEN:
                headers['Authorization'] = f'Bearer {GOFILE_API_TOKEN}'
            
            response = requests.post(GOFILE_UPLOAD_URL, files=files, headers=headers)
            result = response.json()

        # 3. Processar resultado
        if result['status'] == 'ok':
            download_page = result['data']['downloadPage']
            await status_message.edit_text(
                f"✅ Upload concluído com sucesso!\n\n"
                f"📄 Arquivo: {file_name}\n"
                f"🔗 Link: {download_page}"
            )
        else:
            await status_message.edit_text(f"❌ Erro no Gofile: {result.get('error', 'Erro desconhecido')}")

        # Limpar arquivo temporário
        if os.path.exists(file_path):
            os.remove(file_path)

    except Exception as e:
        logging.error(f"Erro ao processar arquivo: {e}")
        await status_message.edit_text(f"❌ Ocorreu um erro: {str(e)}")
        if 'file_path' in locals() and os.path.exists(file_path):
            os.remove(file_path)

if __name__ == '__main__':
    # Verifica se o token foi configurado
    if TELEGRAM_BOT_TOKEN == 'SEU_TELEGRAM_BOT_TOKEN_AQUI':
        print("ERRO: Você precisa configurar o TELEGRAM_BOT_TOKEN no script!")
    else:
        application = ApplicationBuilder().token(TELEGRAM_BOT_TOKEN).build()
        
        # Handlers
        start_handler = CommandHandler('start', start)
        # Filtra quase todos os tipos de mídia
        media_handler = MessageHandler(
            filters.Document.ALL | filters.PHOTO | filters.VIDEO | filters.AUDIO | filters.VOICE, 
            handle_document
        )
        
        application.add_handler(start_handler)
        application.add_handler(media_handler)
        
        print("Bot iniciado... Pressione Ctrl+C para parar.")
        application.run_polling()
