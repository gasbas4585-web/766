package com.example.cosmeticwings;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.io.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Cosmetic Wings — Fabric 1.21.4.
 *
 * Everything cosmetic is kept in this one Java file:
 * GUI, persistent settings, procedural 3D wings, accessories,
 * flight animation and feather fragments.
 *
 * No gameplay/elytra attributes are modified.
 */
public final class CosmeticWingsClient implements ClientModInitializer {
    static MinecraftClient MC;
    static final Identifier WHITE = Identifier.of("cosmeticwings", "textures/white.png");
    static final File CONFIG = new File(System.getProperty("user.home"), ".minecraft/config/cosmeticwings.properties");

    static WingType wings = WingType.ANGEL;
    static boolean halo = true;
    static boolean horns = false;
    static boolean animation = true;
    static FeatherRate featherRate = FeatherRate.NORMAL;
    static float animationPower = 1.0f;
    static float size = 1.0f;

    static KeyBinding menuKey;
    static final List<Feather> FEATHERS = new CopyOnWriteArrayList<>();
    static int featherTimer = 0;
    static float clientTime = 0;

    enum WingType { OFF, ANGEL, DEMON }
    enum FeatherRate { OFF, RARE, NORMAL, OFTEN, VERY_OFTEN }

    @Override
    public void onInitializeClient() {
        MC = MinecraftClient.getInstance();
        load();

        menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.cosmeticwings.menu", InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_LEFT_CONTROL, "category.cosmeticwings"));

        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
            if (renderer instanceof PlayerEntityRenderer) {
                @SuppressWarnings({"rawtypes", "unchecked"})
                FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> ctx =
                        (FeatureRendererContext) renderer;
                helper.register(new WingFeatureRenderer(ctx));
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            clientTime += 0.05f;
            if (menuKey.wasPressed() && client.currentScreen == null) client.setScreen(new CosmeticsScreen());

            updateFeathers();
        });
    }

    static void updateFeathers() {
        if (MC == null || MC.player == null) return;
        for (Feather f : FEATHERS) f.tick();
        FEATHERS.removeIf(f -> f.age > f.maxAge);

        if (wings == WingType.OFF || featherRate == FeatherRate.OFF || !MC.player.isGliding()) {
            featherTimer = 0;
            return;
        }
        featherTimer++;
        int interval = switch (featherRate) {
            case RARE -> 24;
            case NORMAL -> 12;
            case OFTEN -> 6;
            case VERY_OFTEN -> 3;
            default -> 999999;
        };
        if (featherTimer >= interval) {
            featherTimer = 0;
            Random r = new Random();
            int side = r.nextBoolean() ? -1 : 1;
            FEATHERS.add(new Feather(side * (0.5f + r.nextFloat() * 1.3f),
                    0.2f + r.nextFloat() * 1.3f,
                    0.35f + r.nextFloat() * 0.35f,
                    (r.nextFloat() - .5f) * .012f,
                    -(0.01f + r.nextFloat() * .018f),
                    (r.nextFloat() - .5f) * .01f,
                    r.nextFloat() * 6.28f,
                    45 + r.nextInt(30)));
        }
    }

    static class Feather {
        float x,y,z,vx,vy,vz,rot;
        int age,maxAge;
        Feather(float x,float y,float z,float vx,float vy,float vz,float rot,int maxAge){
            this.x=x;this.y=y;this.z=z;this.vx=vx;this.vy=vy;this.vz=vz;this.rot=rot;this.maxAge=maxAge;
        }
        void tick(){ x+=vx; y+=vy; z+=vz; vy-=.00035f; rot+=.08f; age++; }
    }

    static final class WingFeatureRenderer extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {
        WingFeatureRenderer(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) {
            super(context);
        }

        @Override
        public void render(MatrixStack m, VertexConsumerProvider providers, int light,
                           PlayerEntityRenderState state, float limbAngle, float limbDistance) {
            if (wings == WingType.OFF) {
                if (state.id == MC.player.getId() && (halo || horns)) renderHeadExtras(m, providers, light, state, 0f);
                return;
            }

            float gliding = animation ? MathHelper.clamp(state.getGlidingProgress(), 0f, 1f) : 0f;
            float t = clientTime + state.age;
            float flap;
            if (state.isGliding && animation) {
                float phase = t * (wings == WingType.ANGEL ? 0.22f : 0.18f);
                flap = (float)Math.sin(phase) * (wings == WingType.ANGEL ? .26f : .34f) * animationPower;
            } else flap = 0f;

            m.push();
            // Feature renderer is already attached to the player model.
            // Put cosmetics behind the torso.
            m.translate(0, 0.15f, 0.28f);
            m.scale(size, size, size);

            float spread = gliding * 1.0f;
            if (wings == WingType.ANGEL) {
                renderAngel(m, providers, light, gliding, flap, t);
            } else {
                renderDemon(m, providers, light, gliding, flap, t);
            }
            m.pop();

            if (state.id == MC.player.getId() && (halo || horns)) {
                renderHeadExtras(m, providers, light, state, t);
            }
            if (state.id == MC.player.getId() && MC.player.isGliding()) {
                renderFeathers(m, providers, light);
            }
        }

        private void renderAngel(MatrixStack m, VertexConsumerProvider p, int light, float open, float flap, float t) {
            VertexConsumer vc = p.getBuffer(RenderLayer.getEntitySolid(WHITE));
            float fold = 1f - open;
            for (int side : new int[]{-1,1}) {
                renderAngelPanel(m, vc, light, side, -0.15f, 0.80f, 0.55f, open, flap, t, 0);
                renderAngelPanel(m, vc, light, side, -0.12f, 1.22f, 0.25f, open, flap * .9f, t, 1);
            }
            // Extra inner quills make the folded state dense rather than a flat sheet.
            if (open < .25f) {
                for (int side : new int[]{-1,1}) {
                    m.push(); m.translate(side*.24f, .55f, .30f);
                    m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * 14f));
                    feather(m, vc, light, .11f, .70f, .055f, 0xFFF7F0);
                    m.pop();
                }
            }
        }

        private void renderAngelPanel(MatrixStack m, VertexConsumer vc, int light, int side,
                                      float x0, float y0, float z0, float open, float flap, float t, int tier) {
            int count = tier == 0 ? 9 : 7;
            for (int i=0;i<count;i++) {
                float q = i/(float)(count-1);
                float len = tier == 0 ? 1.05f - q*.42f : .82f - q*.34f;
                float y = y0 + q*(tier==0?.62f:.52f);
                float x = side * (Math.abs(x0) + q*(tier==0?1.12f:.92f)*open);
                float z = z0 - q*.24f + (float)Math.sin(t*.14f+i)*.012f;
                float angle = side * (tier==0 ? (-35f + q*24f) : (-50f + q*32f));
                angle += side * MathHelper.RADIANS_PER_DEGREE * (flap * 90f) * (0.55f+q*.45f);
                m.push();
                m.translate(x, y, z);
                m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(angle));
                m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(side * (8f+q*10f)));
                feather(m, vc, light, .14f - q*.035f, len, .055f, 0xFFFDF9);
                m.pop();
            }
        }

        private void renderDemon(MatrixStack m, VertexConsumerProvider p, int light, float open, float flap, float t) {
            VertexConsumer dark = p.getBuffer(RenderLayer.getEntitySolid(WHITE));
            VertexConsumer red = p.getBuffer(RenderLayer.getEntitySolid(WHITE));
            for (int side : new int[]{-1,1}) {
                m.push();
                m.translate(side*.22f, .78f, .30f);
                m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side*(18f + open*38f) + flap*side*70f));
                m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-8f + open*14f));
                cuboid(m,dark,light,-.09f,-.62f,-.10f,.09f,.62f,.10f,0x171116);
                m.pop();

                for(int i=0;i<7;i++){
                    float q=i/6f;
                    float len=1.25f-q*.35f;
                    m.push();
                    m.translate(side*(.42f+q*1.05f*open), .62f+q*.12f, .25f-q*.18f);
                    m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side*(-38f+q*28f)+flap*side*80f));
                    feather(m,dark,light,.18f-q*.015f,len,.08f,0x171116);
                    // red inner membrane strip
                    m.push(); m.translate(0,0,.075f);
                    feather(m,red,light,.055f,len*.78f,.025f,0x7B101C);
                    m.pop();
                    m.pop();

                    if (i < 6) {
                        m.push();
                        m.translate(side*(.48f+q*1.02f*open), .65f+q*.11f, .33f);
                        m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side*(-48f+q*25f)+flap*side*80f));
                        spike(m,dark,light,.13f-q*.012f,.30f-q*.02f,.10f,0xD6C7BA);
                        m.pop();
                    }
                }
            }
        }

        private void renderHeadExtras(MatrixStack m, VertexConsumerProvider p, int light,
                                      PlayerEntityRenderState state, float t) {
            VertexConsumer vc = p.getBuffer(RenderLayer.getEntitySolid(WHITE));
            m.push();
            m.translate(0, -1.05f, 0);
            if (halo) {
                m.push();
                m.translate(0,-.18f,0);
                m.multiply(RotationAxis.POSITIVE_Y.rotation(t*.02f));
                // torus-like halo from 12 small cuboids
                for(int i=0;i<12;i++){
                    double a=i*Math.PI*2/12;
                    m.push();
                    m.translate((float)Math.cos(a)*.28f,0,(float)Math.sin(a)*.28f);
                    m.multiply(RotationAxis.POSITIVE_Y.rotation((float)-a));
                    cuboid(m,vc,light,-.035f,-.025f,-.035f,.035f,.025f,.035f,0xFFF2A8);
                    m.pop();
                }
                m.pop();
            }
            if (horns) {
                for(int side:new int[]{-1,1}) {
                    m.push();
                    m.translate(side*.17f,-.12f,0);
                    m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side*(22f)));
                    spike(m,vc,light,.09f,.48f,.09f,0x5B2020);
                    m.pop();
                }
            }
            m.pop();
        }

        private void renderFeathers(MatrixStack m, VertexConsumerProvider p, int light) {
            VertexConsumer vc=p.getBuffer(RenderLayer.getEntitySolid(WHITE));
            for(Feather f:FEATHERS){
                float alpha=1f-MathHelper.clamp(f.age/(float)f.maxAge,0f,1f);
                m.push(); m.translate(f.x, f.y, f.z);
                m.multiply(RotationAxis.POSITIVE_Z.rotation(f.rot));
                feather(m,vc,light,.045f,.30f,.025f,0xFFFFFF);
                m.pop();
            }
        }

        private void feather(MatrixStack m, VertexConsumer vc, int light, float width, float length, float depth, int color) {
            // A tapered 3D feather: front/back diamond profile + side faces.
            int c=color | 0xFF000000;
            Matrix4f mat=m.peek().getPositionMatrix();
            float w=width, h=length, d=depth;
            v(vc,mat,-w,-h*.45f,-d,c,0,0,light,0,0,-1);
            v(vc,mat, w,-h*.45f,-d,c,1,0,light,0,0,-1);
            v(vc,mat, w*.55f,h*.30f,-d,c,1,1,light,0,0,-1);
            v(vc,mat, 0,h*.55f,-d,c,.5f,1,light,0,0,-1);
            v(vc,mat,-w*.55f,h*.30f,-d,c,0,1,light,0,0,-1);
            v(vc,mat,-w,-h*.45f,d,c,0,0,light,0,0,1);
            v(vc,mat, w,-h*.45f,d,c,1,0,light,0,0,1);
            v(vc,mat, w*.55f,h*.30f,d,c,1,1,light,0,0,1);
            v(vc,mat, 0,h*.55f,d,c,.5f,1,light,0,0,1);
            v(vc,mat,-w*.55f,h*.30f,d,c,0,1,light,0,0,1);
            // sides / top
            quad(vc,mat,-w,-h*.45f,-d,w,-h*.45f,-d,w,-h*.45f,d,-w,-h*.45f,d,c,0,-1,0,light);
            quad(vc,mat,w,-h*.45f,-d,w*.55f,h*.30f,-d,w*.55f,h*.30f,d,w,-h*.45f,d,c,1,0,0,light);
            quad(vc,mat,w*.55f,h*.30f,-d,0,h*.55f,-d,0,h*.55f,d,w*.55f,h*.30f,d,c,1,0,0,light);
            quad(vc,mat,0,h*.55f,-d,-w*.55f,h*.30f,-d,-w*.55f,h*.30f,d,0,h*.55f,d,c,-1,0,0,light);
            quad(vc,mat,-w*.55f,h*.30f,-d,-w,-h*.45f,-d,-w,-h*.45f,d,-w*.55f,h*.30f,d,c,-1,0,0,light);
        }

        private void spike(MatrixStack m, VertexConsumer vc,int light,float w,float h,float d,int color){
            Matrix4f mat=m.peek().getPositionMatrix();
            cuboid(m,vc,light,-w,-h*.5f,-d,w,h*.5f,d,color);
            m.push(); m.translate(0,h*.58f,0); m.scale(.15f,.45f,.15f);
            cuboid(m,vc,light,-w,-h*.5f,-d,w,h*.5f,d,color); m.pop();
        }

        private void cuboid(MatrixStack m,VertexConsumer vc,int light,float x1,float y1,float z1,float x2,float y2,float z2,int color){
            Matrix4f mat=m.peek().getPositionMatrix();
            quad(vc,mat,x1,y1,z1,x2,y1,z1,x2,y2,z1,x1,y2,z1,color,0,0,-1,light);
            quad(vc,mat,x2,y1,z2,x1,y1,z2,x1,y2,z2,x2,y2,z2,color,0,0,1,light);
            quad(vc,mat,x1,y1,z2,x1,y1,z1,x1,y2,z1,x1,y2,z2,color,-1,0,0,light);
            quad(vc,mat,x2,y1,z1,x2,y1,z2,x2,y2,z2,x2,y2,z1,color,1,0,0,light);
            quad(vc,mat,x1,y2,z1,x2,y2,z1,x2,y2,z2,x1,y2,z2,color,0,1,0,light);
            quad(vc,mat,x1,y1,z2,x2,y1,z2,x2,y1,z1,x1,y1,z1,color,0,-1,0,light);
        }

        private void quad(VertexConsumer vc,Matrix4f mat,float ax,float ay,float az,float bx,float by,float bz,
                          float cx,float cy,float cz,float dx,float dy,float dz,int color,float nx,float ny,float nz,int light){
            v(vc,mat,ax,ay,az,color,0,0,light,nx,ny,nz);
            v(vc,mat,bx,by,bz,color,1,0,light,nx,ny,nz);
            v(vc,mat,cx,cy,cz,color,1,1,light,nx,ny,nz);
            v(vc,mat,dx,dy,dz,color,0,1,light,nx,ny,nz);
        }
        private void v(VertexConsumer vc,Matrix4f mat,float x,float y,float z,int c,float u,float vv,int light,float nx,float ny,float nz){
            vc.vertex(mat,x,y,z).color(c).texture(u,vv).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(nx,ny,nz);
        }
    }

    static final class CosmeticsScreen extends Screen {
        CosmeticsScreen(){super(Text.literal("COSMETIC WINGS"));}
        protected void init(){
            int w=300, x=(width-w)/2, y=45;
            addDrawableChild(btn(x,y,w,"КРЫЛЬЯ  •  "+wingLabel(), b->{wings=WingType.values()[(wings.ordinal()+1)%3]; b.setMessage(Text.literal("КРЫЛЬЯ  •  "+wingLabel())); save();}));
            addDrawableChild(btn(x,y+26,w,"НИМБ  •  "+onOff(halo),b->{halo=!halo;b.setMessage(Text.literal("НИМБ  •  "+onOff(halo)));save();}));
            addDrawableChild(btn(x,y+52,w,"РОГА  •  "+onOff(horns),b->{horns=!horns;b.setMessage(Text.literal("РОГА  •  "+onOff(horns)));save();}));
            addDrawableChild(btn(x,y+78,w,"АНИМАЦИЯ  •  "+onOff(animation),b->{animation=!animation;b.setMessage(Text.literal("АНИМАЦИЯ  •  "+onOff(animation)));save();}));
            addDrawableChild(btn(x,y+104,w,"ПЕРЬЯ  •  "+featherLabel(),b->{featherRate=FeatherRate.values()[(featherRate.ordinal()+1)%5];b.setMessage(Text.literal("ПЕРЬЯ  •  "+featherLabel()));save();}));
            addDrawableChild(btn(x,y+130,w,"РАЗМЕР  •  "+Math.round(size*100)+"%",b->{size=size>=1.35f?.75f:size+.15f;b.setMessage(Text.literal("РАЗМЕР  •  "+Math.round(size*100)+"%"));save();}));
            addDrawableChild(btn(x,y+156,w,"СИЛА ВЗМАХА  •  "+Math.round(animationPower*100)+"%",b->{animationPower=animationPower>=1.5f?.5f:animationPower+.25f;b.setMessage(Text.literal("СИЛА ВЗМАХА  •  "+Math.round(animationPower*100)+"%"));save();}));
            addDrawableChild(btn(x,y+188,w,"ГОТОВО",b->close()));
        }
        ButtonWidget btn(int x,int y,int w,String s,java.util.function.Consumer<ButtonWidget> a){
            return ButtonWidget.builder(Text.literal(s),a).dimensions(x,y,w,20).build();
        }
        public void render(DrawContext c,int mx,int my,float d){
            renderBackground(c,mx,my,d);
            c.drawCenteredTextWithShadow(textRenderer,Text.literal("COSMETIC WINGS"),width/2,18,0xD8A6FF);
            c.drawCenteredTextWithShadow(textRenderer,Text.literal("ЛЕВЫЙ CTRL  •  меню"),width/2,height-18,0xAAAAAA);
            super.render(c,mx,my,d);
        }
    }

    static String wingLabel(){return switch(wings){case OFF->"ВЫКЛ";case ANGEL->"ANGEL";case DEMON->"DEMON";};}
    static String onOff(boolean b){return b?"ВКЛ":"ВЫКЛ";}
    static String featherLabel(){return switch(featherRate){case OFF->"ВЫКЛ";case RARE->"РЕДКО";case NORMAL->"НОРМАЛЬНО";case OFTEN->"ЧАСТО";case VERY_OFTEN->"ОЧЕНЬ ЧАСТО";};}

    static void load(){
        if(!CONFIG.exists()) return;
        Properties p=new Properties();
        try(FileInputStream in=new FileInputStream(CONFIG)){
            p.load(in);
            wings=WingType.valueOf(p.getProperty("wings","ANGEL"));
            halo=Boolean.parseBoolean(p.getProperty("halo","true"));
            horns=Boolean.parseBoolean(p.getProperty("horns","false"));
            animation=Boolean.parseBoolean(p.getProperty("animation","true"));
            featherRate=FeatherRate.valueOf(p.getProperty("feathers","NORMAL"));
            animationPower=Float.parseFloat(p.getProperty("animationPower","1.0"));
            size=Float.parseFloat(p.getProperty("size","1.0"));
        }catch(Exception ignored){}
    }
    static void save(){
        try{
            File parent=CONFIG.getParentFile(); if(parent!=null) parent.mkdirs();
            Properties p=new Properties();
            p.setProperty("wings",wings.name());p.setProperty("halo",Boolean.toString(halo));
            p.setProperty("horns",Boolean.toString(horns));p.setProperty("animation",Boolean.toString(animation));
            p.setProperty("feathers",featherRate.name());p.setProperty("animationPower",Float.toString(animationPower));
            p.setProperty("size",Float.toString(size));
            try(FileOutputStream out=new FileOutputStream(CONFIG)){p.store(out,"Cosmetic Wings settings");}
        }catch(IOException ignored){}
    }
}
