import os
import time
import logging
import requests
from telegram import Update
from telegram.ext import ApplicationBuilder, ContextTypes, MessageHandler, filters, CommandHandler

# --- CONFIGURAÇÕES ---
TELEGRAM_BOT_TOKEN = '8343442752:AAHZbleu8_exX0Oxt5CuTA4L-E6LMxhxfps'
GOFILE_API_TOKEN = 'dMdC3Ivd2QGnoD4TX9QGn6NM88PSQb8T' 
# ---------------------

logging.basicConfig(format='%(asctime)s - %(name)s - %(levelname)s - %(message)s', level=logging.INFO)

def create_progress_bar(current, total):
    """Cria uma barra de progresso visual [■■■□□□]"""
    if total <= 0: return "[░░░░░░░░░░] 0%"
    percentage = (current / total) * 100
    finished_blocks = int(percentage / 10)
    remaining_blocks = 10 - finished_blocks
    return f"[{'■' * finished_blocks}{'□' * remaining_blocks}] {percentage:.1f}%"

async def edit_message_safe(message, text, last_update_time):
    """Atualiza a mensagem apenas a cada 2.5 segundos para evitar ban do Telegram."""
    current_time = time.time()
    if current_time - last_update_time[0] > 2.5:
        try:
            await message.edit_text(text)
            last_update_time[0] = current_time
        except Exception:
            pass

def get_best_server():
    try:
        res = requests.get("https://api.gofile.io/servers").json()
        return res['data']['servers'][0]['name'] if res['status'] == 'ok' else "store1"
    except:
        return "store1"

async def start(update: Update, context: ContextTypes.DEFAULT_TYPE):
    await update.message.reply_text("✅ Bot Ativo! Envie um arquivo para testar o upload com progresso.")

async def handle_document(update: Update, context: ContextTypes.DEFAULT_TYPE):
    msg = update.message
    # Tenta pegar qualquer tipo de mídia enviada
    file_obj = msg.document or msg.video or msg.audio or (msg.photo[-1] if msg.photo else None)
    
    if not file_obj:
        return
    
    file_name = getattr(file_obj, 'file_name', f"arquivo_{file_obj.file_unique_id}.jpg")
    status_msg = await msg.reply_text("⏳ Preparando...")
    last_update = [time.time()]

    try:
        # 1. PEGAR O ARQUIVO DO TELEGRAM
        tg_file = await context.bot.get_file(file_obj.file_id)
        file_url = tg_file.file_path
        file_path = f"temp_{file_name}"

        # 2. DOWNLOAD COM PROGRESSO
        response = requests.get(file_url, stream=True)
        total_size = int(response.headers.get('content-length', 0))
        downloaded = 0
        
        with open(file_path, 'wb') as f:
            for chunk in response.iter_content(chunk_size=1024*512): # 512KB
                if chunk:
                    f.write(chunk)
                    downloaded += len(chunk)
                    bar = create_progress_bar(downloaded, total_size)
                    await edit_message_safe(status_msg, f"📥 Baixando do Telegram:\n{bar}", last_update)

        await status_msg.edit_text("🔍 Buscando melhor servidor Gofile...")
        server = get_best_server()
        
        # 3. UPLOAD (O requests não mostra progresso nativo facilmente, 
        # mas vamos avisar o usuário que começou)
        await status_msg.edit_text(f"📤 Enviando ao servidor {server}...\n(Isso pode demorar dependendo do tamanho)")

        upload_url = f"https://{server}.gofile.io/contents/uploadfile"
        
        with open(file_path, 'rb') as f:
            files = {'file': (file_name, f)}
            data = {'token': GOFILE_API_TOKEN} if GOFILE_API_TOKEN else {}
            response = requests.post(upload_url, files=files, data=data)
            
        result = response.json()

        if result['status'] == 'ok':
            link = result['data']['downloadPage']
            await status_msg.edit_text(f"✅ **Upload Concluído!**\n\n📄 Arquivo: `{file_name}`\n🔗 Link: {link}", parse_mode='Markdown')
        else:
            await status_msg.edit_text(f"❌ Erro no Gofile: {result.get('error', 'Erro desconhecido')}")

    except Exception as e:
        logging.error(f"Erro: {e}")
        await status_msg.edit_text(f"❌ Ocorreu um erro: {str(e)}")
    finally:
        if 'file_path' in locals() and os.path.exists(file_path):
            os.remove(file_path)

if __name__ == '__main__':
    # A SOLUÇÃO PARA O ERRO DE TYPEERROR:
    # Desativamos o job_queue pois não é necessário e causa erro de fuso horário no Codespaces/Docker
    application = ApplicationBuilder().token(TELEGRAM_BOT_TOKEN).job_queue(None).build()
    
    application.add_handler(CommandHandler('start', start))
    application.add_handler(MessageHandler(filters.ALL & ~filters.COMMAND, handle_document))
    
    print("Bot rodando sem JobQueue (fix de Timezone aplicado)...")
    application.run_polling()