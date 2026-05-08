package com.yang.lblogserver.aspect;

import com.yang.lblogserver.domain.ImageUsage;
import com.yang.lblogserver.mapper.ImageUsageMapper;
import com.yang.lblogserver.service.ImagesService;
import com.yang.lblogserver.service.PostContentsService;
import com.yang.lblogserver.vo.admin.CreatePostRequest;
import com.yang.lblogserver.vo.admin.UpdatePostRequest;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Aspect
@Component
public class ImageUsageAspect {

    private static final Logger log = LoggerFactory.getLogger(ImageUsageAspect.class);
    private static final Pattern IMG_TAG_PATTERN = Pattern.compile("<img[^>]+src\\s*=\\s*\"([^\"]+)\"");
    private static final Pattern MD_IMAGE_PATTERN = Pattern.compile("!\\[[^\\]]*\\]\\(([^)]+)\\)");

    private final ImagesService imagesService;
    private final ImageUsageMapper imageUsageMapper;
    private final PostContentsService postContentsService;

    public ImageUsageAspect(ImagesService imagesService, ImageUsageMapper imageUsageMapper,
                            PostContentsService postContentsService) {
        this.imagesService = imagesService;
        this.imageUsageMapper = imageUsageMapper;
        this.postContentsService = postContentsService;
    }

    @AfterReturning(pointcut = "execution(* com.yang.lblogserver.service.PostsService.createPost(..))", returning = "postId")
    @Transactional(rollbackFor = Exception.class)
    public void afterCreatePost(JoinPoint jp, Object postId) {
        if (postId == null) return;
        Object[] args = jp.getArgs();
        if (args.length < 1 || !(args[0] instanceof CreatePostRequest req)) return;
        syncImageUsages((Long) postId, req.getBody(), req.getFeaturedImage());
    }

    @AfterReturning("execution(* com.yang.lblogserver.service.PostsService.updatePost(..))")
    @Transactional(rollbackFor = Exception.class)
    public void afterUpdatePost(JoinPoint jp) {
        Object[] args = jp.getArgs();
        if (args.length < 2 || !(args[0] instanceof Long postId) || !(args[1] instanceof UpdatePostRequest req)) return;

        String body = req.getBody();
        if (body == null) {
            var contents = postContentsService.getByPostId(postId);
            body = contents != null ? contents.getBody() : null;
        }
        syncImageUsages(postId, body, req.getFeaturedImage());
    }

    @AfterReturning("execution(* com.yang.lblogserver.service.PostsService.deletePost(..))")
    @Transactional(rollbackFor = Exception.class)
    public void afterDeletePost(JoinPoint jp) {
        Object[] args = jp.getArgs();
        if (args.length < 1 || !(args[0] instanceof Long postId)) return;
        imageUsageMapper.deleteByRef("post", postId);
        log.debug("Cleared image usages for deleted post {}", postId);
    }

    private void syncImageUsages(Long postId, String body, String featuredImage) {
        imageUsageMapper.deleteByRef("post", postId);

        Set<String> urls = extractImageUrls(body);
        for (String url : urls) {
            var img = imagesService.findByUrl(url);
            if (img != null) {
                ImageUsage usage = new ImageUsage();
                usage.setImageId(img.getId());
                usage.setRefType("post");
                usage.setRefId(postId);
                usage.setField("body");
                imageUsageMapper.insert(usage);
            }
        }

        if (featuredImage != null && !featuredImage.isBlank()) {
            var img = imagesService.findByUrl(featuredImage);
            if (img != null) {
                ImageUsage usage = new ImageUsage();
                usage.setImageId(img.getId());
                usage.setRefType("post");
                usage.setRefId(postId);
                usage.setField("featured_image");
                imageUsageMapper.insert(usage);
            }
        }

        log.debug("Synced image usages for post {} ({} images)", postId, urls.size());
    }

    private Set<String> extractImageUrls(String body) {
        Set<String> urls = new HashSet<>();
        if (body == null || body.isBlank()) return urls;
        Matcher m = IMG_TAG_PATTERN.matcher(body);
        while (m.find()) urls.add(m.group(1));
        m = MD_IMAGE_PATTERN.matcher(body);
        while (m.find()) urls.add(m.group(1));
        return urls;
    }
}
